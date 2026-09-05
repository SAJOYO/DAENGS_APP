#!/usr/bin/env python3
"""Run real DAO SQL against exported Room schema. Does not replace Kotlin/Compose tests.

    python scripts/test_walk_entry_boundaries.py
"""
import json
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
DAO = ROOT / 'app/src/main/java/com/daengs/app/walk/store/WalkDao.kt'


def query(method):
    source = DAO.read_text()
    pattern = r'@Query\("([^"\n]*)"\)\s+suspend fun ' + method + r'\('
    match = re.search(pattern, source)
    if not match:
        raise AssertionError(f'No single-string DAO query: {method}')
    return match.group(1)


class WalkEntryBoundaries(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        self.db.row_factory = sqlite3.Row
        self.db.execute('PRAGMA foreign_keys = ON')
        schema = json.loads((ROOT / 'app/schemas/com.daengs.app.walk.store.WalkDatabase/11.json').read_text())
        for entity in schema['database']['entities']:
            self.db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        self.session('s', 'a')
        self.entry('e', 's')

    def tearDown(self):
        self.db.close()

    def session(self, sid, owner):
        self.db.execute('INSERT INTO walk_session (id, ownerId, startedAtMillis, syncState) VALUES (?, ?, 0, ?)',
                        (sid, owner, 'local_only'))

    def entry(self, eid, sid):
        self.db.execute('INSERT INTO walk_entry VALUES (?, ?, ?, 1, ?, 0, NULL)',
                        (eid, sid, '{"pet_id":"dog-a"}', 'first'))

    def row(self):
        return self.db.execute('SELECT * FROM walk_entry WHERE id = ?', ('e',)).fetchone()

    def edit(self, revision=1, mutation='first', session='s'):
        return self.db.execute(query('editEntryIfUnchanged'), dict(id='e', sessionId=session,
            payload='{"pet_id":"dog-a","behavior_code":"barking"}', mutationId='draft',
            baseRevision=revision, baseMutationId=mutation)).rowcount

    def test_remote_pet_correction_rejects_open_draft(self):
        self.db.execute(query('acceptEntry'), dict(id='e', payload='{"pet_id":"dog-b"}',
            revision=2, mutationId='remote'))
        self.assertEqual(0, self.edit())
        self.assertEqual('dog-b', json.loads(self.row()['payload'])['pet_id'])
        self.assertEqual(0, self.row()['dirty'])
        self.assertEqual(1, self.edit(2, 'remote'))

    def test_local_edit_at_same_revision_rejects_other_draft(self):
        self.assertEqual(1, self.edit())
        self.assertEqual(0, self.edit())
        self.assertEqual('draft', self.row()['mutationId'])

    def test_wrong_session_and_missing_version_cannot_edit(self):
        self.assertEqual(0, self.edit(session='other'))
        self.assertEqual(0, self.edit(None, None))

    def test_conflicted_dirty_entry_accepts_remote_deletion(self):
        self.edit()
        self.db.execute(query('conflictEntry'), dict(id='e', revision=2, mutationId='draft', message='conflict'))
        for _ in range(3):
            self.db.execute(query('acceptDeletedEntry'), dict(id='e', revision=3))
        self.assertIsNone(self.row()['payload'])
        self.assertIsNone(self.row()['syncError'])
        self.assertEqual(0, self.row()['dirty'])
        self.assertEqual(0, self.edit(3, 'draft'))

    def test_late_ack_preserves_undo_tombstone_for_next_delivery(self):
        self.db.execute(query('editEntry'), dict(id='e', payload=None, mutationId='undo'))
        self.db.execute(query('acknowledgeEntry'), dict(id='e', revision=2, mutationId='first'))
        self.assertIsNone(self.row()['payload'])
        self.assertEqual(1, self.row()['dirty'])
        self.assertEqual('undo', self.row()['mutationId'])
        self.db.execute(query('acknowledgeEntry'), dict(id='e', revision=3, mutationId='undo'))
        self.assertEqual(0, self.row()['dirty'])

    def test_explicit_withdrawal_cascades_only_target_owner(self):
        for sid, owner in [('b-session', 'b'), ('guest', '')]:
            self.session(sid, owner)
            self.entry(sid + '-entry', sid)
        self.db.execute('INSERT INTO walk_fix VALUES (?, 0, 0, 1, 37.5, 127.0, 5, 0)', ('s',))
        self.db.execute(query('deleteOwnerSessions'), dict(ownerId='a'))
        self.assertIsNone(self.row())
        self.assertEqual(0, self.db.execute('SELECT COUNT(*) FROM walk_fix').fetchone()[0])
        self.assertEqual(['b-session', 'guest'], [r[0] for r in self.db.execute('SELECT id FROM walk_session ORDER BY id')])
        self.assertEqual(2, self.db.execute('SELECT COUNT(*) FROM walk_entry').fetchone()[0])
        with self.assertRaises(sqlite3.IntegrityError):
            self.entry('late-child', 's')


if __name__ == '__main__':
    unittest.main(verbosity=2)
