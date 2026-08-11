package com.spark.wallet.data.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.spark.wallet.data.entity.LocalPurse;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
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
public final class LocalPurseDao_Impl implements LocalPurseDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<LocalPurse> __insertionAdapterOfLocalPurse;

  private final EntityDeletionOrUpdateAdapter<LocalPurse> __updateAdapterOfLocalPurse;

  private final SharedSQLiteStatement __preparedStmtOfUpdateRemainingAndCounter;

  private final SharedSQLiteStatement __preparedStmtOfDeletePurse;

  private final SharedSQLiteStatement __preparedStmtOfClearAll;

  public LocalPurseDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfLocalPurse = new EntityInsertionAdapter<LocalPurse>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `local_purse` (`token_id`,`cap`,`remaining`,`counter_current`,`signed_token_blob`,`expires_at`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final LocalPurse entity) {
        statement.bindString(1, entity.getTokenId());
        statement.bindLong(2, entity.getCap());
        statement.bindLong(3, entity.getRemaining());
        statement.bindLong(4, entity.getCounterCurrent());
        statement.bindString(5, entity.getSignedTokenBlob());
        statement.bindLong(6, entity.getExpiresAt());
      }
    };
    this.__updateAdapterOfLocalPurse = new EntityDeletionOrUpdateAdapter<LocalPurse>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `local_purse` SET `token_id` = ?,`cap` = ?,`remaining` = ?,`counter_current` = ?,`signed_token_blob` = ?,`expires_at` = ? WHERE `token_id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final LocalPurse entity) {
        statement.bindString(1, entity.getTokenId());
        statement.bindLong(2, entity.getCap());
        statement.bindLong(3, entity.getRemaining());
        statement.bindLong(4, entity.getCounterCurrent());
        statement.bindString(5, entity.getSignedTokenBlob());
        statement.bindLong(6, entity.getExpiresAt());
        statement.bindString(7, entity.getTokenId());
      }
    };
    this.__preparedStmtOfUpdateRemainingAndCounter = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE local_purse SET remaining = ?, counter_current = ? WHERE token_id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeletePurse = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM local_purse WHERE token_id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM local_purse";
        return _query;
      }
    };
  }

  @Override
  public Object insertPurse(final LocalPurse purse, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfLocalPurse.insert(purse);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updatePurse(final LocalPurse purse, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfLocalPurse.handle(purse);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateRemainingAndCounter(final String tokenId, final long remaining,
      final long counter, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateRemainingAndCounter.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, remaining);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, counter);
        _argIndex = 3;
        _stmt.bindString(_argIndex, tokenId);
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
          __preparedStmtOfUpdateRemainingAndCounter.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deletePurse(final String tokenId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeletePurse.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, tokenId);
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
          __preparedStmtOfDeletePurse.release(_stmt);
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
  public Object getPurseByTokenId(final String tokenId,
      final Continuation<? super LocalPurse> $completion) {
    final String _sql = "SELECT * FROM local_purse WHERE token_id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, tokenId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<LocalPurse>() {
      @Override
      @Nullable
      public LocalPurse call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTokenId = CursorUtil.getColumnIndexOrThrow(_cursor, "token_id");
          final int _cursorIndexOfCap = CursorUtil.getColumnIndexOrThrow(_cursor, "cap");
          final int _cursorIndexOfRemaining = CursorUtil.getColumnIndexOrThrow(_cursor, "remaining");
          final int _cursorIndexOfCounterCurrent = CursorUtil.getColumnIndexOrThrow(_cursor, "counter_current");
          final int _cursorIndexOfSignedTokenBlob = CursorUtil.getColumnIndexOrThrow(_cursor, "signed_token_blob");
          final int _cursorIndexOfExpiresAt = CursorUtil.getColumnIndexOrThrow(_cursor, "expires_at");
          final LocalPurse _result;
          if (_cursor.moveToFirst()) {
            final String _tmpTokenId;
            _tmpTokenId = _cursor.getString(_cursorIndexOfTokenId);
            final long _tmpCap;
            _tmpCap = _cursor.getLong(_cursorIndexOfCap);
            final long _tmpRemaining;
            _tmpRemaining = _cursor.getLong(_cursorIndexOfRemaining);
            final long _tmpCounterCurrent;
            _tmpCounterCurrent = _cursor.getLong(_cursorIndexOfCounterCurrent);
            final String _tmpSignedTokenBlob;
            _tmpSignedTokenBlob = _cursor.getString(_cursorIndexOfSignedTokenBlob);
            final long _tmpExpiresAt;
            _tmpExpiresAt = _cursor.getLong(_cursorIndexOfExpiresAt);
            _result = new LocalPurse(_tmpTokenId,_tmpCap,_tmpRemaining,_tmpCounterCurrent,_tmpSignedTokenBlob,_tmpExpiresAt);
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
  public Object getActivePurse(final Continuation<? super LocalPurse> $completion) {
    final String _sql = "SELECT * FROM local_purse WHERE remaining > 0 ORDER BY expires_at DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<LocalPurse>() {
      @Override
      @Nullable
      public LocalPurse call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTokenId = CursorUtil.getColumnIndexOrThrow(_cursor, "token_id");
          final int _cursorIndexOfCap = CursorUtil.getColumnIndexOrThrow(_cursor, "cap");
          final int _cursorIndexOfRemaining = CursorUtil.getColumnIndexOrThrow(_cursor, "remaining");
          final int _cursorIndexOfCounterCurrent = CursorUtil.getColumnIndexOrThrow(_cursor, "counter_current");
          final int _cursorIndexOfSignedTokenBlob = CursorUtil.getColumnIndexOrThrow(_cursor, "signed_token_blob");
          final int _cursorIndexOfExpiresAt = CursorUtil.getColumnIndexOrThrow(_cursor, "expires_at");
          final LocalPurse _result;
          if (_cursor.moveToFirst()) {
            final String _tmpTokenId;
            _tmpTokenId = _cursor.getString(_cursorIndexOfTokenId);
            final long _tmpCap;
            _tmpCap = _cursor.getLong(_cursorIndexOfCap);
            final long _tmpRemaining;
            _tmpRemaining = _cursor.getLong(_cursorIndexOfRemaining);
            final long _tmpCounterCurrent;
            _tmpCounterCurrent = _cursor.getLong(_cursorIndexOfCounterCurrent);
            final String _tmpSignedTokenBlob;
            _tmpSignedTokenBlob = _cursor.getString(_cursorIndexOfSignedTokenBlob);
            final long _tmpExpiresAt;
            _tmpExpiresAt = _cursor.getLong(_cursorIndexOfExpiresAt);
            _result = new LocalPurse(_tmpTokenId,_tmpCap,_tmpRemaining,_tmpCounterCurrent,_tmpSignedTokenBlob,_tmpExpiresAt);
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
  public Flow<List<LocalPurse>> getAllPursesFlow() {
    final String _sql = "SELECT * FROM local_purse ORDER BY expires_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"local_purse"}, new Callable<List<LocalPurse>>() {
      @Override
      @NonNull
      public List<LocalPurse> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTokenId = CursorUtil.getColumnIndexOrThrow(_cursor, "token_id");
          final int _cursorIndexOfCap = CursorUtil.getColumnIndexOrThrow(_cursor, "cap");
          final int _cursorIndexOfRemaining = CursorUtil.getColumnIndexOrThrow(_cursor, "remaining");
          final int _cursorIndexOfCounterCurrent = CursorUtil.getColumnIndexOrThrow(_cursor, "counter_current");
          final int _cursorIndexOfSignedTokenBlob = CursorUtil.getColumnIndexOrThrow(_cursor, "signed_token_blob");
          final int _cursorIndexOfExpiresAt = CursorUtil.getColumnIndexOrThrow(_cursor, "expires_at");
          final List<LocalPurse> _result = new ArrayList<LocalPurse>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalPurse _item;
            final String _tmpTokenId;
            _tmpTokenId = _cursor.getString(_cursorIndexOfTokenId);
            final long _tmpCap;
            _tmpCap = _cursor.getLong(_cursorIndexOfCap);
            final long _tmpRemaining;
            _tmpRemaining = _cursor.getLong(_cursorIndexOfRemaining);
            final long _tmpCounterCurrent;
            _tmpCounterCurrent = _cursor.getLong(_cursorIndexOfCounterCurrent);
            final String _tmpSignedTokenBlob;
            _tmpSignedTokenBlob = _cursor.getString(_cursorIndexOfSignedTokenBlob);
            final long _tmpExpiresAt;
            _tmpExpiresAt = _cursor.getLong(_cursorIndexOfExpiresAt);
            _item = new LocalPurse(_tmpTokenId,_tmpCap,_tmpRemaining,_tmpCounterCurrent,_tmpSignedTokenBlob,_tmpExpiresAt);
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
  public Object getAllPurses(final Continuation<? super List<LocalPurse>> $completion) {
    final String _sql = "SELECT * FROM local_purse ORDER BY expires_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<LocalPurse>>() {
      @Override
      @NonNull
      public List<LocalPurse> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTokenId = CursorUtil.getColumnIndexOrThrow(_cursor, "token_id");
          final int _cursorIndexOfCap = CursorUtil.getColumnIndexOrThrow(_cursor, "cap");
          final int _cursorIndexOfRemaining = CursorUtil.getColumnIndexOrThrow(_cursor, "remaining");
          final int _cursorIndexOfCounterCurrent = CursorUtil.getColumnIndexOrThrow(_cursor, "counter_current");
          final int _cursorIndexOfSignedTokenBlob = CursorUtil.getColumnIndexOrThrow(_cursor, "signed_token_blob");
          final int _cursorIndexOfExpiresAt = CursorUtil.getColumnIndexOrThrow(_cursor, "expires_at");
          final List<LocalPurse> _result = new ArrayList<LocalPurse>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalPurse _item;
            final String _tmpTokenId;
            _tmpTokenId = _cursor.getString(_cursorIndexOfTokenId);
            final long _tmpCap;
            _tmpCap = _cursor.getLong(_cursorIndexOfCap);
            final long _tmpRemaining;
            _tmpRemaining = _cursor.getLong(_cursorIndexOfRemaining);
            final long _tmpCounterCurrent;
            _tmpCounterCurrent = _cursor.getLong(_cursorIndexOfCounterCurrent);
            final String _tmpSignedTokenBlob;
            _tmpSignedTokenBlob = _cursor.getString(_cursorIndexOfSignedTokenBlob);
            final long _tmpExpiresAt;
            _tmpExpiresAt = _cursor.getLong(_cursorIndexOfExpiresAt);
            _item = new LocalPurse(_tmpTokenId,_tmpCap,_tmpRemaining,_tmpCounterCurrent,_tmpSignedTokenBlob,_tmpExpiresAt);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
