#!/usr/bin/env python3
"""Exercise the actual SQL schema embedded in the new Java store using CPython sqlite3."""
from pathlib import Path
import sqlite3, re
src=Path(__file__).resolve().parents[1]/'app/src/main/java/ru/reader/rsvp'

def sqls(java: str):
    for call in re.findall(r'db\.execSQL\((.*?)\);',java,re.DOTALL):
        strings=re.findall(r'"((?:\\.|[^"\\])*)"',call)
        if strings:
            yield ''.join(strings)

sql=list(sqls((src/'ReaderQuotesStore.java').read_text()))
assert len(sql)>=3,sql
con=sqlite3.connect(':memory:')
for stmt in sql:con.execute(stmt)
insert='''INSERT INTO quotes(book,book_title,chapter,from_char,to_char,from_word,to_word,
    original,text,pet_enabled,created_ms,updated_ms) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)'''
con.execute(insert,('book-a','A','Chapter 1',3,27,2,5,'Hello world','Hello world',1,100,100))
try:
    con.execute(insert,('book-a','A','Chapter 1',3,27,2,5,'Duplicate','Duplicate',1,110,110))
    raise AssertionError('A duplicate source range must be rejected')
except sqlite3.IntegrityError: pass
con.execute(insert,('book-b','B','Chapter 2',3,27,2,5,'Hello world','Hello world',1,150,150))
con.execute(insert,('book-a','A','Chapter 3',30,70,8,13,'Another quote','Another quote',1,200,200))
assert con.execute('SELECT COUNT(*) FROM quotes').fetchone()[0]==3
# All-books companion pool is deduplicated by displayed text.
rows=con.execute('SELECT MIN(id) FROM quotes WHERE pet_enabled=1 AND LENGTH(TRIM(text))>0 GROUP BY LOWER(TRIM(text))').fetchall()
assert len(rows)==2,rows
con.execute('UPDATE quotes SET text=? WHERE book=? AND from_char=?',('Edited','book-a',3))
assert con.execute('SELECT text FROM quotes WHERE book=? AND from_char=?',('book-a',3)).fetchone()[0]=='Edited'
con.execute('UPDATE quotes SET pet_enabled=0 WHERE book=?',('book-a',))
assert con.execute('SELECT COUNT(*) FROM quotes WHERE pet_enabled=1').fetchone()[0]==1
con.execute('DELETE FROM quotes WHERE book=?',('book-b',))
assert con.execute('SELECT COUNT(*) FROM quotes WHERE pet_enabled=1').fetchone()[0]==0
# Older note DB records survive the additive schema migration.
legacy=sqlite3.connect(':memory:')
legacy.execute('''CREATE TABLE annotations (id INTEGER PRIMARY KEY AUTOINCREMENT,
  book TEXT NOT NULL, from_word INTEGER NOT NULL,to_word INTEGER NOT NULL,excerpt TEXT NOT NULL,
  context TEXT NOT NULL DEFAULT '',color INTEGER NOT NULL,note TEXT NOT NULL DEFAULT '',updated_ms INTEGER NOT NULL)''')
legacy.execute("INSERT INTO annotations(book,from_word,to_word,excerpt,color,note,updated_ms) VALUES ('a',3,5,'text',0,'note',100)")
java=(src/'ReaderAnnotationsStore.java').read_text()
for statement in re.findall(r'db\.execSQL\("(ALTER TABLE annotations[^\"]*)"\);',java): legacy.execute(statement)
assert legacy.execute('SELECT from_char,to_char,note FROM annotations').fetchone()==(-1,-1,'note')
print('PASS: v184 SQLite quotes CRUD, deduplication, companion flags and lossless annotation migration')
