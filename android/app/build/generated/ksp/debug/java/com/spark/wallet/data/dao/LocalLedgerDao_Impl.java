package com.spark.wallet.data.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.spark.wallet.data.entity.LocalLedgerEntry;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class LocalLedgerDao_Impl implements LocalLedgerDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<LocalLedgerEntry> __insertionAdapterOfLocalLedgerEntry;

  private final SharedSQLiteStatement __preparedStmtOfMarkSynced;

  private final SharedSQLiteStatement __preparedStmtOfClearAll;

  public LocalLedgerDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfLocalLedgerEntry = new EntityInsertionAdapter<LocalLedgerEntry>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `local_ledger` (`tx_id`,`direction`,`counterparty_id`,`amount`,`counter`,`prev_hash`,`hash`,`signature`,`timestamp`,`synced`) VALUES (?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final LocalLedgerEntry entity) {
        statement.bindString(1, entity.getTxId());
        statement.bindString(2, entity.getDirection());
        statement.bindString(3, entity.getCounterpartyId());
        statement.bindLong(4, entity.getAmount());
        statement.bindLong(5, entity.getCounter());
        if (entity.getPrevHash() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getPrevHash());
        }
        statement.bindString(7, entity.getHash());
        statement.bindString(8, entity.getSignature());
        statement.bindLong(9, entity.getTimestamp());
        final int _tmp = entity.getSynced() ? 1 : 0;
        statement.bindLong(10, _tmp);
      }
    };
    this.__preparedStmtOfMarkSynced = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE local_ledger SET synced = 1 WHERE tx_id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM local_ledger";
        return _query;
      }
    };
  }

  @Override
  public Object insertEntry(final LocalLedgerEntry entry,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfLocalLedgerEntry.insert(entry);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<LocalLedgerEntry> entries,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfLocalLedgerEntry.insert(entries);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object markSynced(final String txId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkSynced.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, txId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfMarkSynced.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getEntryById(final String txId,
      final Continuation<? super LocalLedgerEntry> $completion) {
    final String _sql = "SELECT * FROM local_ledger WHERE tx_id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, txId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<LocalLedgerEntry>() {
      @Override
      @Nullable
      public LocalLedgerEntry call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTxId = CursorUtil.getColumnIndexOrThrow(_cursor, "tx_id");
          final int _cursorIndexOfDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "direction");
          final int _cursorIndexOfCounterpartyId = CursorUtil.getColumnIndexOrThrow(_cursor, "counterparty_id");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfCounter = CursorUtil.getColumnIndexOrThrow(_cursor, "counter");
          final int _cursorIndexOfPrevHash = CursorUtil.getColumnIndexOrThrow(_cursor, "prev_hash");
          final int _cursorIndexOfHash = CursorUtil.getColumnIndexOrThrow(_cursor, "hash");
          final int _cursorIndexOfSignature = CursorUtil.getColumnIndexOrThrow(_cursor, "signature");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
          final LocalLedgerEntry _result;
          if (_cursor.moveToFirst()) {
            final String _tmpTxId;
            _tmpTxId = _cursor.getString(_cursorIndexOfTxId);
            final String _tmpDirection;
            _tmpDirection = _cursor.getString(_cursorIndexOfDirection);
            final String _tmpCounterpartyId;
            _tmpCounterpartyId = _cursor.getString(_cursorIndexOfCounterpartyId);
            final long _tmpAmount;
            _tmpAmount = _cursor.getLong(_cursorIndexOfAmount);
            final long _tmpCounter;
            _tmpCounter = _cursor.getLong(_cursorIndexOfCounter);
            final String _tmpPrevHash;
            if (_cursor.isNull(_cursorIndexOfPrevHash)) {
              _tmpPrevHash = null;
            } else {
              _tmpPrevHash = _cursor.getString(_cursorIndexOfPrevHash);
            }
            final String _tmpHash;
            _tmpHash = _cursor.getString(_cursorIndexOfHash);
            final String _tmpSignature;
            _tmpSignature = _cursor.getString(_cursorIndexOfSignature);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSynced);
            _tmpSynced = _tmp != 0;
            _result = new LocalLedgerEntry(_tmpTxId,_tmpDirection,_tmpCounterpartyId,_tmpAmount,_tmpCounter,_tmpPrevHash,_tmpHash,_tmpSignature,_tmpTimestamp,_tmpSynced);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<LocalLedgerEntry>> getAllEntriesFlow() {
    final String _sql = "SELECT * FROM local_ledger ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"local_ledger"}, new Callable<List<LocalLedgerEntry>>() {
      @Override
      @NonNull
      public List<LocalLedgerEntry> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTxId = CursorUtil.getColumnIndexOrThrow(_cursor, "tx_id");
          final int _cursorIndexOfDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "direction");
          final int _cursorIndexOfCounterpartyId = CursorUtil.getColumnIndexOrThrow(_cursor, "counterparty_id");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfCounter = CursorUtil.getColumnIndexOrThrow(_cursor, "counter");
          final int _cursorIndexOfPrevHash = CursorUtil.getColumnIndexOrThrow(_cursor, "prev_hash");
          final int _cursorIndexOfHash = CursorUtil.getColumnIndexOrThrow(_cursor, "hash");
          final int _cursorIndexOfSignature = CursorUtil.getColumnIndexOrThrow(_cursor, "signature");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
          final List<LocalLedgerEntry> _result = new ArrayList<LocalLedgerEntry>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalLedgerEntry _item;
            final String _tmpTxId;
            _tmpTxId = _cursor.getString(_cursorIndexOfTxId);
            final String _tmpDirection;
            _tmpDirection = _cursor.getString(_cursorIndexOfDirection);
            final String _tmpCounterpartyId;
            _tmpCounterpartyId = _cursor.getString(_cursorIndexOfCounterpartyId);
            final long _tmpAmount;
            _tmpAmount = _cursor.getLong(_cursorIndexOfAmount);
            final long _tmpCounter;
            _tmpCounter = _cursor.getLong(_cursorIndexOfCounter);
            final String _tmpPrevHash;
            if (_cursor.isNull(_cursorIndexOfPrevHash)) {
              _tmpPrevHash = null;
            } else {
              _tmpPrevHash = _cursor.getString(_cursorIndexOfPrevHash);
            }
            final String _tmpHash;
            _tmpHash = _cursor.getString(_cursorIndexOfHash);
            final String _tmpSignature;
            _tmpSignature = _cursor.getString(_cursorIndexOfSignature);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSynced);
            _tmpSynced = _tmp != 0;
            _item = new LocalLedgerEntry(_tmpTxId,_tmpDirection,_tmpCounterpartyId,_tmpAmount,_tmpCounter,_tmpPrevHash,_tmpHash,_tmpSignature,_tmpTimestamp,_tmpSynced);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getAllEntries(final Continuation<? super List<LocalLedgerEntry>> $completion) {
    final String _sql = "SELECT * FROM local_ledger ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<LocalLedgerEntry>>() {
      @Override
      @NonNull
      public List<LocalLedgerEntry> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTxId = CursorUtil.getColumnIndexOrThrow(_cursor, "tx_id");
          final int _cursorIndexOfDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "direction");
          final int _cursorIndexOfCounterpartyId = CursorUtil.getColumnIndexOrThrow(_cursor, "counterparty_id");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfCounter = CursorUtil.getColumnIndexOrThrow(_cursor, "counter");
          final int _cursorIndexOfPrevHash = CursorUtil.getColumnIndexOrThrow(_cursor, "prev_hash");
          final int _cursorIndexOfHash = CursorUtil.getColumnIndexOrThrow(_cursor, "hash");
          final int _cursorIndexOfSignature = CursorUtil.getColumnIndexOrThrow(_cursor, "signature");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
          final List<LocalLedgerEntry> _result = new ArrayList<LocalLedgerEntry>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalLedgerEntry _item;
            final String _tmpTxId;
            _tmpTxId = _cursor.getString(_cursorIndexOfTxId);
            final String _tmpDirection;
            _tmpDirection = _cursor.getString(_cursorIndexOfDirection);
            final String _tmpCounterpartyId;
            _tmpCounterpartyId = _cursor.getString(_cursorIndexOfCounterpartyId);
            final long _tmpAmount;
            _tmpAmount = _cursor.getLong(_cursorIndexOfAmount);
            final long _tmpCounter;
            _tmpCounter = _cursor.getLong(_cursorIndexOfCounter);
            final String _tmpPrevHash;
            if (_cursor.isNull(_cursorIndexOfPrevHash)) {
              _tmpPrevHash = null;
            } else {
              _tmpPrevHash = _cursor.getString(_cursorIndexOfPrevHash);
            }
            final String _tmpHash;
            _tmpHash = _cursor.getString(_cursorIndexOfHash);
            final String _tmpSignature;
            _tmpSignature = _cursor.getString(_cursorIndexOfSignature);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSynced);
            _tmpSynced = _tmp != 0;
            _item = new LocalLedgerEntry(_tmpTxId,_tmpDirection,_tmpCounterpartyId,_tmpAmount,_tmpCounter,_tmpPrevHash,_tmpHash,_tmpSignature,_tmpTimestamp,_tmpSynced);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getUnsyncedEntries(final Continuation<? super List<LocalLedgerEntry>> $completion) {
    final String _sql = "SELECT * FROM local_ledger WHERE synced = 0 ORDER BY counter ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<LocalLedgerEntry>>() {
      @Override
      @NonNull
      public List<LocalLedgerEntry> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTxId = CursorUtil.getColumnIndexOrThrow(_cursor, "tx_id");
          final int _cursorIndexOfDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "direction");
          final int _cursorIndexOfCounterpartyId = CursorUtil.getColumnIndexOrThrow(_cursor, "counterparty_id");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfCounter = CursorUtil.getColumnIndexOrThrow(_cursor, "counter");
          final int _cursorIndexOfPrevHash = CursorUtil.getColumnIndexOrThrow(_cursor, "prev_hash");
          final int _cursorIndexOfHash = CursorUtil.getColumnIndexOrThrow(_cursor, "hash");
          final int _cursorIndexOfSignature = CursorUtil.getColumnIndexOrThrow(_cursor, "signature");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
          final List<LocalLedgerEntry> _result = new ArrayList<LocalLedgerEntry>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalLedgerEntry _item;
            final String _tmpTxId;
            _tmpTxId = _cursor.getString(_cursorIndexOfTxId);
            final String _tmpDirection;
            _tmpDirection = _cursor.getString(_cursorIndexOfDirection);
            final String _tmpCounterpartyId;
            _tmpCounterpartyId = _cursor.getString(_cursorIndexOfCounterpartyId);
            final long _tmpAmount;
            _tmpAmount = _cursor.getLong(_cursorIndexOfAmount);
            final long _tmpCounter;
            _tmpCounter = _cursor.getLong(_cursorIndexOfCounter);
            final String _tmpPrevHash;
            if (_cursor.isNull(_cursorIndexOfPrevHash)) {
              _tmpPrevHash = null;
            } else {
              _tmpPrevHash = _cursor.getString(_cursorIndexOfPrevHash);
            }
            final String _tmpHash;
            _tmpHash = _cursor.getString(_cursorIndexOfHash);
            final String _tmpSignature;
            _tmpSignature = _cursor.getString(_cursorIndexOfSignature);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSynced);
            _tmpSynced = _tmp != 0;
            _item = new LocalLedgerEntry(_tmpTxId,_tmpDirection,_tmpCounterpartyId,_tmpAmount,_tmpCounter,_tmpPrevHash,_tmpHash,_tmpSignature,_tmpTimestamp,_tmpSynced);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getLatestEntry(final Continuation<? super LocalLedgerEntry> $completion) {
    final String _sql = "SELECT * FROM local_ledger ORDER BY counter DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<LocalLedgerEntry>() {
      @Override
      @Nullable
      public LocalLedgerEntry call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTxId = CursorUtil.getColumnIndexOrThrow(_cursor, "tx_id");
          final int _cursorIndexOfDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "direction");
          final int _cursorIndexOfCounterpartyId = CursorUtil.getColumnIndexOrThrow(_cursor, "counterparty_id");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfCounter = CursorUtil.getColumnIndexOrThrow(_cursor, "counter");
          final int _cursorIndexOfPrevHash = CursorUtil.getColumnIndexOrThrow(_cursor, "prev_hash");
          final int _cursorIndexOfHash = CursorUtil.getColumnIndexOrThrow(_cursor, "hash");
          final int _cursorIndexOfSignature = CursorUtil.getColumnIndexOrThrow(_cursor, "signature");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
          final LocalLedgerEntry _result;
          if (_cursor.moveToFirst()) {
            final String _tmpTxId;
            _tmpTxId = _cursor.getString(_cursorIndexOfTxId);
            final String _tmpDirection;
            _tmpDirection = _cursor.getString(_cursorIndexOfDirection);
            final String _tmpCounterpartyId;
            _tmpCounterpartyId = _cursor.getString(_cursorIndexOfCounterpartyId);
            final long _tmpAmount;
            _tmpAmount = _cursor.getLong(_cursorIndexOfAmount);
            final long _tmpCounter;
            _tmpCounter = _cursor.getLong(_cursorIndexOfCounter);
            final String _tmpPrevHash;
            if (_cursor.isNull(_cursorIndexOfPrevHash)) {
              _tmpPrevHash = null;
            } else {
              _tmpPrevHash = _cursor.getString(_cursorIndexOfPrevHash);
            }
            final String _tmpHash;
            _tmpHash = _cursor.getString(_cursorIndexOfHash);
            final String _tmpSignature;
            _tmpSignature = _cursor.getString(_cursorIndexOfSignature);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSynced);
            _tmpSynced = _tmp != 0;
            _result = new LocalLedgerEntry(_tmpTxId,_tmpDirection,_tmpCounterpartyId,_tmpAmount,_tmpCounter,_tmpPrevHash,_tmpHash,_tmpSignature,_tmpTimestamp,_tmpSynced);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getEntriesForCounterparty(final String counterpartyId,
      final Continuation<? super List<LocalLedgerEntry>> $completion) {
    final String _sql = "SELECT * FROM local_ledger WHERE counterparty_id = ? ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, counterpartyId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<LocalLedgerEntry>>() {
      @Override
      @NonNull
      public List<LocalLedgerEntry> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTxId = CursorUtil.getColumnIndexOrThrow(_cursor, "tx_id");
          final int _cursorIndexOfDirection = CursorUtil.getColumnIndexOrThrow(_cursor, "direction");
          final int _cursorIndexOfCounterpartyId = CursorUtil.getColumnIndexOrThrow(_cursor, "counterparty_id");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfCounter = CursorUtil.getColumnIndexOrThrow(_cursor, "counter");
          final int _cursorIndexOfPrevHash = CursorUtil.getColumnIndexOrThrow(_cursor, "prev_hash");
          final int _cursorIndexOfHash = CursorUtil.getColumnIndexOrThrow(_cursor, "hash");
          final int _cursorIndexOfSignature = CursorUtil.getColumnIndexOrThrow(_cursor, "signature");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfSynced = CursorUtil.getColumnIndexOrThrow(_cursor, "synced");
          final List<LocalLedgerEntry> _result = new ArrayList<LocalLedgerEntry>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalLedgerEntry _item;
            final String _tmpTxId;
            _tmpTxId = _cursor.getString(_cursorIndexOfTxId);
            final String _tmpDirection;
            _tmpDirection = _cursor.getString(_cursorIndexOfDirection);
            final String _tmpCounterpartyId;
            _tmpCounterpartyId = _cursor.getString(_cursorIndexOfCounterpartyId);
            final long _tmpAmount;
            _tmpAmount = _cursor.getLong(_cursorIndexOfAmount);
            final long _tmpCounter;
            _tmpCounter = _cursor.getLong(_cursorIndexOfCounter);
            final String _tmpPrevHash;
            if (_cursor.isNull(_cursorIndexOfPrevHash)) {
              _tmpPrevHash = null;
            } else {
              _tmpPrevHash = _cursor.getString(_cursorIndexOfPrevHash);
            }
            final String _tmpHash;
            _tmpHash = _cursor.getString(_cursorIndexOfHash);
            final String _tmpSignature;
            _tmpSignature = _cursor.getString(_cursorIndexOfSignature);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final boolean _tmpSynced;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSynced);
            _tmpSynced = _tmp != 0;
            _item = new LocalLedgerEntry(_tmpTxId,_tmpDirection,_tmpCounterpartyId,_tmpAmount,_tmpCounter,_tmpPrevHash,_tmpHash,_tmpSignature,_tmpTimestamp,_tmpSynced);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object markAllSynced(final List<String> txIds,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("UPDATE local_ledger SET synced = 1 WHERE tx_id IN (");
        final int _inputSize = txIds.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        for (String _item : txIds) {
          _stmt.bindString(_argIndex, _item);
          _argIndex++;
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
