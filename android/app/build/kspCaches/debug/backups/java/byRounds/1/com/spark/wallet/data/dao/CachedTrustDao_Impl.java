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
import androidx.sqlite.db.SupportSQLiteStatement;
import com.spark.wallet.data.entity.CachedTrust;
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
public final class CachedTrustDao_Impl implements CachedTrustDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<CachedTrust> __insertionAdapterOfCachedTrust;

  private final SharedSQLiteStatement __preparedStmtOfDeleteTrust;

  private final SharedSQLiteStatement __preparedStmtOfClearAll;

  public CachedTrustDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfCachedTrust = new EntityInsertionAdapter<CachedTrust>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `cached_trust` (`subject_id`,`trust_score`,`attestation_blobs`,`cached_at`) VALUES (?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CachedTrust entity) {
        statement.bindString(1, entity.getSubjectId());
        statement.bindDouble(2, entity.getTrustScore());
        statement.bindString(3, entity.getAttestationBlobs());
        statement.bindLong(4, entity.getCachedAt());
      }
    };
    this.__preparedStmtOfDeleteTrust = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM cached_trust WHERE subject_id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM cached_trust";
        return _query;
      }
    };
  }

  @Override
  public Object insertTrust(final CachedTrust trust, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfCachedTrust.insert(trust);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<CachedTrust> trusts,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfCachedTrust.insert(trusts);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteTrust(final String subjectId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteTrust.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, subjectId);
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
          __preparedStmtOfDeleteTrust.release(_stmt);
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
  public Object getTrustBySubjectId(final String subjectId,
      final Continuation<? super CachedTrust> $completion) {
    final String _sql = "SELECT * FROM cached_trust WHERE subject_id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, subjectId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<CachedTrust>() {
      @Override
      @Nullable
      public CachedTrust call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSubjectId = CursorUtil.getColumnIndexOrThrow(_cursor, "subject_id");
          final int _cursorIndexOfTrustScore = CursorUtil.getColumnIndexOrThrow(_cursor, "trust_score");
          final int _cursorIndexOfAttestationBlobs = CursorUtil.getColumnIndexOrThrow(_cursor, "attestation_blobs");
          final int _cursorIndexOfCachedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "cached_at");
          final CachedTrust _result;
          if (_cursor.moveToFirst()) {
            final String _tmpSubjectId;
            _tmpSubjectId = _cursor.getString(_cursorIndexOfSubjectId);
            final double _tmpTrustScore;
            _tmpTrustScore = _cursor.getDouble(_cursorIndexOfTrustScore);
            final String _tmpAttestationBlobs;
            _tmpAttestationBlobs = _cursor.getString(_cursorIndexOfAttestationBlobs);
            final long _tmpCachedAt;
            _tmpCachedAt = _cursor.getLong(_cursorIndexOfCachedAt);
            _result = new CachedTrust(_tmpSubjectId,_tmpTrustScore,_tmpAttestationBlobs,_tmpCachedAt);
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
  public Object getAllTrust(final Continuation<? super List<CachedTrust>> $completion) {
    final String _sql = "SELECT * FROM cached_trust ORDER BY cached_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<CachedTrust>>() {
      @Override
      @NonNull
      public List<CachedTrust> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSubjectId = CursorUtil.getColumnIndexOrThrow(_cursor, "subject_id");
          final int _cursorIndexOfTrustScore = CursorUtil.getColumnIndexOrThrow(_cursor, "trust_score");
          final int _cursorIndexOfAttestationBlobs = CursorUtil.getColumnIndexOrThrow(_cursor, "attestation_blobs");
          final int _cursorIndexOfCachedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "cached_at");
          final List<CachedTrust> _result = new ArrayList<CachedTrust>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CachedTrust _item;
            final String _tmpSubjectId;
            _tmpSubjectId = _cursor.getString(_cursorIndexOfSubjectId);
            final double _tmpTrustScore;
            _tmpTrustScore = _cursor.getDouble(_cursorIndexOfTrustScore);
            final String _tmpAttestationBlobs;
            _tmpAttestationBlobs = _cursor.getString(_cursorIndexOfAttestationBlobs);
            final long _tmpCachedAt;
            _tmpCachedAt = _cursor.getLong(_cursorIndexOfCachedAt);
            _item = new CachedTrust(_tmpSubjectId,_tmpTrustScore,_tmpAttestationBlobs,_tmpCachedAt);
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
  public Flow<List<CachedTrust>> getAllTrustFlow() {
    final String _sql = "SELECT * FROM cached_trust ORDER BY cached_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"cached_trust"}, new Callable<List<CachedTrust>>() {
      @Override
      @NonNull
      public List<CachedTrust> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSubjectId = CursorUtil.getColumnIndexOrThrow(_cursor, "subject_id");
          final int _cursorIndexOfTrustScore = CursorUtil.getColumnIndexOrThrow(_cursor, "trust_score");
          final int _cursorIndexOfAttestationBlobs = CursorUtil.getColumnIndexOrThrow(_cursor, "attestation_blobs");
          final int _cursorIndexOfCachedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "cached_at");
          final List<CachedTrust> _result = new ArrayList<CachedTrust>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CachedTrust _item;
            final String _tmpSubjectId;
            _tmpSubjectId = _cursor.getString(_cursorIndexOfSubjectId);
            final double _tmpTrustScore;
            _tmpTrustScore = _cursor.getDouble(_cursorIndexOfTrustScore);
            final String _tmpAttestationBlobs;
            _tmpAttestationBlobs = _cursor.getString(_cursorIndexOfAttestationBlobs);
            final long _tmpCachedAt;
            _tmpCachedAt = _cursor.getLong(_cursorIndexOfCachedAt);
            _item = new CachedTrust(_tmpSubjectId,_tmpTrustScore,_tmpAttestationBlobs,_tmpCachedAt);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
