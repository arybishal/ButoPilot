package com.autopilot.android.data.local.dao;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.autopilot.android.data.local.entity.ExecutionHistoryEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
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
public final class ExecutionHistoryDao_Impl implements ExecutionHistoryDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ExecutionHistoryEntity> __insertionAdapterOfExecutionHistoryEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfClearAll;

  public ExecutionHistoryDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfExecutionHistoryEntity = new EntityInsertionAdapter<ExecutionHistoryEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `execution_history` (`id`,`profileId`,`profileName`,`startedAt`,`endedAt`,`durationSeconds`,`totalActions`,`status`,`errorMessage`) VALUES (?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ExecutionHistoryEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getProfileId());
        statement.bindString(3, entity.getProfileName());
        statement.bindLong(4, entity.getStartedAt());
        if (entity.getEndedAt() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getEndedAt());
        }
        statement.bindLong(6, entity.getDurationSeconds());
        statement.bindLong(7, entity.getTotalActions());
        statement.bindString(8, entity.getStatus());
        if (entity.getErrorMessage() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getErrorMessage());
        }
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM execution_history WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM execution_history";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final ExecutionHistoryEntity item,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfExecutionHistoryEntity.insert(item);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, id);
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
          __preparedStmtOfDeleteById.release(_stmt);
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
  public Flow<List<ExecutionHistoryEntity>> getAllFlow() {
    final String _sql = "SELECT * FROM execution_history ORDER BY startedAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"execution_history"}, new Callable<List<ExecutionHistoryEntity>>() {
      @Override
      @NonNull
      public List<ExecutionHistoryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfProfileId = CursorUtil.getColumnIndexOrThrow(_cursor, "profileId");
          final int _cursorIndexOfProfileName = CursorUtil.getColumnIndexOrThrow(_cursor, "profileName");
          final int _cursorIndexOfStartedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "startedAt");
          final int _cursorIndexOfEndedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "endedAt");
          final int _cursorIndexOfDurationSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "durationSeconds");
          final int _cursorIndexOfTotalActions = CursorUtil.getColumnIndexOrThrow(_cursor, "totalActions");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfErrorMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "errorMessage");
          final List<ExecutionHistoryEntity> _result = new ArrayList<ExecutionHistoryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ExecutionHistoryEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpProfileId;
            _tmpProfileId = _cursor.getString(_cursorIndexOfProfileId);
            final String _tmpProfileName;
            _tmpProfileName = _cursor.getString(_cursorIndexOfProfileName);
            final long _tmpStartedAt;
            _tmpStartedAt = _cursor.getLong(_cursorIndexOfStartedAt);
            final Long _tmpEndedAt;
            if (_cursor.isNull(_cursorIndexOfEndedAt)) {
              _tmpEndedAt = null;
            } else {
              _tmpEndedAt = _cursor.getLong(_cursorIndexOfEndedAt);
            }
            final long _tmpDurationSeconds;
            _tmpDurationSeconds = _cursor.getLong(_cursorIndexOfDurationSeconds);
            final int _tmpTotalActions;
            _tmpTotalActions = _cursor.getInt(_cursorIndexOfTotalActions);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final String _tmpErrorMessage;
            if (_cursor.isNull(_cursorIndexOfErrorMessage)) {
              _tmpErrorMessage = null;
            } else {
              _tmpErrorMessage = _cursor.getString(_cursorIndexOfErrorMessage);
            }
            _item = new ExecutionHistoryEntity(_tmpId,_tmpProfileId,_tmpProfileName,_tmpStartedAt,_tmpEndedAt,_tmpDurationSeconds,_tmpTotalActions,_tmpStatus,_tmpErrorMessage);
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
  public Flow<List<ExecutionHistoryEntity>> getForProfile(final String profileId) {
    final String _sql = "SELECT * FROM execution_history WHERE profileId = ? ORDER BY startedAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, profileId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"execution_history"}, new Callable<List<ExecutionHistoryEntity>>() {
      @Override
      @NonNull
      public List<ExecutionHistoryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfProfileId = CursorUtil.getColumnIndexOrThrow(_cursor, "profileId");
          final int _cursorIndexOfProfileName = CursorUtil.getColumnIndexOrThrow(_cursor, "profileName");
          final int _cursorIndexOfStartedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "startedAt");
          final int _cursorIndexOfEndedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "endedAt");
          final int _cursorIndexOfDurationSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "durationSeconds");
          final int _cursorIndexOfTotalActions = CursorUtil.getColumnIndexOrThrow(_cursor, "totalActions");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfErrorMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "errorMessage");
          final List<ExecutionHistoryEntity> _result = new ArrayList<ExecutionHistoryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ExecutionHistoryEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpProfileId;
            _tmpProfileId = _cursor.getString(_cursorIndexOfProfileId);
            final String _tmpProfileName;
            _tmpProfileName = _cursor.getString(_cursorIndexOfProfileName);
            final long _tmpStartedAt;
            _tmpStartedAt = _cursor.getLong(_cursorIndexOfStartedAt);
            final Long _tmpEndedAt;
            if (_cursor.isNull(_cursorIndexOfEndedAt)) {
              _tmpEndedAt = null;
            } else {
              _tmpEndedAt = _cursor.getLong(_cursorIndexOfEndedAt);
            }
            final long _tmpDurationSeconds;
            _tmpDurationSeconds = _cursor.getLong(_cursorIndexOfDurationSeconds);
            final int _tmpTotalActions;
            _tmpTotalActions = _cursor.getInt(_cursorIndexOfTotalActions);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final String _tmpErrorMessage;
            if (_cursor.isNull(_cursorIndexOfErrorMessage)) {
              _tmpErrorMessage = null;
            } else {
              _tmpErrorMessage = _cursor.getString(_cursorIndexOfErrorMessage);
            }
            _item = new ExecutionHistoryEntity(_tmpId,_tmpProfileId,_tmpProfileName,_tmpStartedAt,_tmpEndedAt,_tmpDurationSeconds,_tmpTotalActions,_tmpStatus,_tmpErrorMessage);
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
