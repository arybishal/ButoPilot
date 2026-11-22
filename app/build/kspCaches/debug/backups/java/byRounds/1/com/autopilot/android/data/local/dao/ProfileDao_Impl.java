package com.autopilot.android.data.local.dao;

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
import com.autopilot.android.data.local.entity.ProfileEntity;
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
public final class ProfileDao_Impl implements ProfileDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ProfileEntity> __insertionAdapterOfProfileEntity;

  private final EntityDeletionOrUpdateAdapter<ProfileEntity> __deletionAdapterOfProfileEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfUpdateStatus;

  public ProfileDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfProfileEntity = new EntityInsertionAdapter<ProfileEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `automation_profiles` (`id`,`name`,`description`,`urlsJson`,`tabCount`,`executionMode`,`refreshIntervalSec`,`scrollIntervalSec`,`randomizeIntervals`,`minRefreshIntervalSec`,`maxRefreshIntervalSec`,`minScrollIntervalSec`,`maxScrollIntervalSec`,`customScript`,`allowedDomainsJson`,`userAgent`,`clearCookiesOnStart`,`clearCacheOnStart`,`timeoutSeconds`,`createdAt`,`updatedAt`,`status`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ProfileEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getDescription());
        statement.bindString(4, entity.getUrlsJson());
        statement.bindLong(5, entity.getTabCount());
        statement.bindString(6, entity.getExecutionMode());
        statement.bindLong(7, entity.getRefreshIntervalSec());
        statement.bindLong(8, entity.getScrollIntervalSec());
        final int _tmp = entity.getRandomizeIntervals() ? 1 : 0;
        statement.bindLong(9, _tmp);
        statement.bindLong(10, entity.getMinRefreshIntervalSec());
        statement.bindLong(11, entity.getMaxRefreshIntervalSec());
        statement.bindLong(12, entity.getMinScrollIntervalSec());
        statement.bindLong(13, entity.getMaxScrollIntervalSec());
        if (entity.getCustomScript() == null) {
          statement.bindNull(14);
        } else {
          statement.bindString(14, entity.getCustomScript());
        }
        statement.bindString(15, entity.getAllowedDomainsJson());
        if (entity.getUserAgent() == null) {
          statement.bindNull(16);
        } else {
          statement.bindString(16, entity.getUserAgent());
        }
        final int _tmp_1 = entity.getClearCookiesOnStart() ? 1 : 0;
        statement.bindLong(17, _tmp_1);
        final int _tmp_2 = entity.getClearCacheOnStart() ? 1 : 0;
        statement.bindLong(18, _tmp_2);
        statement.bindLong(19, entity.getTimeoutSeconds());
        statement.bindLong(20, entity.getCreatedAt());
        statement.bindLong(21, entity.getUpdatedAt());
        statement.bindString(22, entity.getStatus());
      }
    };
    this.__deletionAdapterOfProfileEntity = new EntityDeletionOrUpdateAdapter<ProfileEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `automation_profiles` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ProfileEntity entity) {
        statement.bindString(1, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM automation_profiles WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE automation_profiles SET status = ?, updatedAt = ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertOrUpdate(final ProfileEntity profile,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfProfileEntity.insert(profile);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final ProfileEntity profile, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfProfileEntity.handle(profile);
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
  public Object updateStatus(final String id, final String status, final long updatedAt,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateStatus.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, status);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, updatedAt);
        _argIndex = 3;
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
          __preparedStmtOfUpdateStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ProfileEntity>> getAllFlow() {
    final String _sql = "SELECT * FROM automation_profiles ORDER BY updatedAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"automation_profiles"}, new Callable<List<ProfileEntity>>() {
      @Override
      @NonNull
      public List<ProfileEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfUrlsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "urlsJson");
          final int _cursorIndexOfTabCount = CursorUtil.getColumnIndexOrThrow(_cursor, "tabCount");
          final int _cursorIndexOfExecutionMode = CursorUtil.getColumnIndexOrThrow(_cursor, "executionMode");
          final int _cursorIndexOfRefreshIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "refreshIntervalSec");
          final int _cursorIndexOfScrollIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "scrollIntervalSec");
          final int _cursorIndexOfRandomizeIntervals = CursorUtil.getColumnIndexOrThrow(_cursor, "randomizeIntervals");
          final int _cursorIndexOfMinRefreshIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "minRefreshIntervalSec");
          final int _cursorIndexOfMaxRefreshIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "maxRefreshIntervalSec");
          final int _cursorIndexOfMinScrollIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "minScrollIntervalSec");
          final int _cursorIndexOfMaxScrollIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "maxScrollIntervalSec");
          final int _cursorIndexOfCustomScript = CursorUtil.getColumnIndexOrThrow(_cursor, "customScript");
          final int _cursorIndexOfAllowedDomainsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "allowedDomainsJson");
          final int _cursorIndexOfUserAgent = CursorUtil.getColumnIndexOrThrow(_cursor, "userAgent");
          final int _cursorIndexOfClearCookiesOnStart = CursorUtil.getColumnIndexOrThrow(_cursor, "clearCookiesOnStart");
          final int _cursorIndexOfClearCacheOnStart = CursorUtil.getColumnIndexOrThrow(_cursor, "clearCacheOnStart");
          final int _cursorIndexOfTimeoutSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "timeoutSeconds");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final List<ProfileEntity> _result = new ArrayList<ProfileEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ProfileEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final String _tmpUrlsJson;
            _tmpUrlsJson = _cursor.getString(_cursorIndexOfUrlsJson);
            final int _tmpTabCount;
            _tmpTabCount = _cursor.getInt(_cursorIndexOfTabCount);
            final String _tmpExecutionMode;
            _tmpExecutionMode = _cursor.getString(_cursorIndexOfExecutionMode);
            final int _tmpRefreshIntervalSec;
            _tmpRefreshIntervalSec = _cursor.getInt(_cursorIndexOfRefreshIntervalSec);
            final int _tmpScrollIntervalSec;
            _tmpScrollIntervalSec = _cursor.getInt(_cursorIndexOfScrollIntervalSec);
            final boolean _tmpRandomizeIntervals;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfRandomizeIntervals);
            _tmpRandomizeIntervals = _tmp != 0;
            final int _tmpMinRefreshIntervalSec;
            _tmpMinRefreshIntervalSec = _cursor.getInt(_cursorIndexOfMinRefreshIntervalSec);
            final int _tmpMaxRefreshIntervalSec;
            _tmpMaxRefreshIntervalSec = _cursor.getInt(_cursorIndexOfMaxRefreshIntervalSec);
            final int _tmpMinScrollIntervalSec;
            _tmpMinScrollIntervalSec = _cursor.getInt(_cursorIndexOfMinScrollIntervalSec);
            final int _tmpMaxScrollIntervalSec;
            _tmpMaxScrollIntervalSec = _cursor.getInt(_cursorIndexOfMaxScrollIntervalSec);
            final String _tmpCustomScript;
            if (_cursor.isNull(_cursorIndexOfCustomScript)) {
              _tmpCustomScript = null;
            } else {
              _tmpCustomScript = _cursor.getString(_cursorIndexOfCustomScript);
            }
            final String _tmpAllowedDomainsJson;
            _tmpAllowedDomainsJson = _cursor.getString(_cursorIndexOfAllowedDomainsJson);
            final String _tmpUserAgent;
            if (_cursor.isNull(_cursorIndexOfUserAgent)) {
              _tmpUserAgent = null;
            } else {
              _tmpUserAgent = _cursor.getString(_cursorIndexOfUserAgent);
            }
            final boolean _tmpClearCookiesOnStart;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfClearCookiesOnStart);
            _tmpClearCookiesOnStart = _tmp_1 != 0;
            final boolean _tmpClearCacheOnStart;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfClearCacheOnStart);
            _tmpClearCacheOnStart = _tmp_2 != 0;
            final int _tmpTimeoutSeconds;
            _tmpTimeoutSeconds = _cursor.getInt(_cursorIndexOfTimeoutSeconds);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            _item = new ProfileEntity(_tmpId,_tmpName,_tmpDescription,_tmpUrlsJson,_tmpTabCount,_tmpExecutionMode,_tmpRefreshIntervalSec,_tmpScrollIntervalSec,_tmpRandomizeIntervals,_tmpMinRefreshIntervalSec,_tmpMaxRefreshIntervalSec,_tmpMinScrollIntervalSec,_tmpMaxScrollIntervalSec,_tmpCustomScript,_tmpAllowedDomainsJson,_tmpUserAgent,_tmpClearCookiesOnStart,_tmpClearCacheOnStart,_tmpTimeoutSeconds,_tmpCreatedAt,_tmpUpdatedAt,_tmpStatus);
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
  public Object getAll(final Continuation<? super List<ProfileEntity>> $completion) {
    final String _sql = "SELECT * FROM automation_profiles ORDER BY updatedAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ProfileEntity>>() {
      @Override
      @NonNull
      public List<ProfileEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfUrlsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "urlsJson");
          final int _cursorIndexOfTabCount = CursorUtil.getColumnIndexOrThrow(_cursor, "tabCount");
          final int _cursorIndexOfExecutionMode = CursorUtil.getColumnIndexOrThrow(_cursor, "executionMode");
          final int _cursorIndexOfRefreshIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "refreshIntervalSec");
          final int _cursorIndexOfScrollIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "scrollIntervalSec");
          final int _cursorIndexOfRandomizeIntervals = CursorUtil.getColumnIndexOrThrow(_cursor, "randomizeIntervals");
          final int _cursorIndexOfMinRefreshIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "minRefreshIntervalSec");
          final int _cursorIndexOfMaxRefreshIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "maxRefreshIntervalSec");
          final int _cursorIndexOfMinScrollIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "minScrollIntervalSec");
          final int _cursorIndexOfMaxScrollIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "maxScrollIntervalSec");
          final int _cursorIndexOfCustomScript = CursorUtil.getColumnIndexOrThrow(_cursor, "customScript");
          final int _cursorIndexOfAllowedDomainsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "allowedDomainsJson");
          final int _cursorIndexOfUserAgent = CursorUtil.getColumnIndexOrThrow(_cursor, "userAgent");
          final int _cursorIndexOfClearCookiesOnStart = CursorUtil.getColumnIndexOrThrow(_cursor, "clearCookiesOnStart");
          final int _cursorIndexOfClearCacheOnStart = CursorUtil.getColumnIndexOrThrow(_cursor, "clearCacheOnStart");
          final int _cursorIndexOfTimeoutSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "timeoutSeconds");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final List<ProfileEntity> _result = new ArrayList<ProfileEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ProfileEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final String _tmpUrlsJson;
            _tmpUrlsJson = _cursor.getString(_cursorIndexOfUrlsJson);
            final int _tmpTabCount;
            _tmpTabCount = _cursor.getInt(_cursorIndexOfTabCount);
            final String _tmpExecutionMode;
            _tmpExecutionMode = _cursor.getString(_cursorIndexOfExecutionMode);
            final int _tmpRefreshIntervalSec;
            _tmpRefreshIntervalSec = _cursor.getInt(_cursorIndexOfRefreshIntervalSec);
            final int _tmpScrollIntervalSec;
            _tmpScrollIntervalSec = _cursor.getInt(_cursorIndexOfScrollIntervalSec);
            final boolean _tmpRandomizeIntervals;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfRandomizeIntervals);
            _tmpRandomizeIntervals = _tmp != 0;
            final int _tmpMinRefreshIntervalSec;
            _tmpMinRefreshIntervalSec = _cursor.getInt(_cursorIndexOfMinRefreshIntervalSec);
            final int _tmpMaxRefreshIntervalSec;
            _tmpMaxRefreshIntervalSec = _cursor.getInt(_cursorIndexOfMaxRefreshIntervalSec);
            final int _tmpMinScrollIntervalSec;
            _tmpMinScrollIntervalSec = _cursor.getInt(_cursorIndexOfMinScrollIntervalSec);
            final int _tmpMaxScrollIntervalSec;
            _tmpMaxScrollIntervalSec = _cursor.getInt(_cursorIndexOfMaxScrollIntervalSec);
            final String _tmpCustomScript;
            if (_cursor.isNull(_cursorIndexOfCustomScript)) {
              _tmpCustomScript = null;
            } else {
              _tmpCustomScript = _cursor.getString(_cursorIndexOfCustomScript);
            }
            final String _tmpAllowedDomainsJson;
            _tmpAllowedDomainsJson = _cursor.getString(_cursorIndexOfAllowedDomainsJson);
            final String _tmpUserAgent;
            if (_cursor.isNull(_cursorIndexOfUserAgent)) {
              _tmpUserAgent = null;
            } else {
              _tmpUserAgent = _cursor.getString(_cursorIndexOfUserAgent);
            }
            final boolean _tmpClearCookiesOnStart;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfClearCookiesOnStart);
            _tmpClearCookiesOnStart = _tmp_1 != 0;
            final boolean _tmpClearCacheOnStart;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfClearCacheOnStart);
            _tmpClearCacheOnStart = _tmp_2 != 0;
            final int _tmpTimeoutSeconds;
            _tmpTimeoutSeconds = _cursor.getInt(_cursorIndexOfTimeoutSeconds);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            _item = new ProfileEntity(_tmpId,_tmpName,_tmpDescription,_tmpUrlsJson,_tmpTabCount,_tmpExecutionMode,_tmpRefreshIntervalSec,_tmpScrollIntervalSec,_tmpRandomizeIntervals,_tmpMinRefreshIntervalSec,_tmpMaxRefreshIntervalSec,_tmpMinScrollIntervalSec,_tmpMaxScrollIntervalSec,_tmpCustomScript,_tmpAllowedDomainsJson,_tmpUserAgent,_tmpClearCookiesOnStart,_tmpClearCacheOnStart,_tmpTimeoutSeconds,_tmpCreatedAt,_tmpUpdatedAt,_tmpStatus);
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
  public Object getById(final String id, final Continuation<? super ProfileEntity> $completion) {
    final String _sql = "SELECT * FROM automation_profiles WHERE id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ProfileEntity>() {
      @Override
      @Nullable
      public ProfileEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfUrlsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "urlsJson");
          final int _cursorIndexOfTabCount = CursorUtil.getColumnIndexOrThrow(_cursor, "tabCount");
          final int _cursorIndexOfExecutionMode = CursorUtil.getColumnIndexOrThrow(_cursor, "executionMode");
          final int _cursorIndexOfRefreshIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "refreshIntervalSec");
          final int _cursorIndexOfScrollIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "scrollIntervalSec");
          final int _cursorIndexOfRandomizeIntervals = CursorUtil.getColumnIndexOrThrow(_cursor, "randomizeIntervals");
          final int _cursorIndexOfMinRefreshIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "minRefreshIntervalSec");
          final int _cursorIndexOfMaxRefreshIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "maxRefreshIntervalSec");
          final int _cursorIndexOfMinScrollIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "minScrollIntervalSec");
          final int _cursorIndexOfMaxScrollIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "maxScrollIntervalSec");
          final int _cursorIndexOfCustomScript = CursorUtil.getColumnIndexOrThrow(_cursor, "customScript");
          final int _cursorIndexOfAllowedDomainsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "allowedDomainsJson");
          final int _cursorIndexOfUserAgent = CursorUtil.getColumnIndexOrThrow(_cursor, "userAgent");
          final int _cursorIndexOfClearCookiesOnStart = CursorUtil.getColumnIndexOrThrow(_cursor, "clearCookiesOnStart");
          final int _cursorIndexOfClearCacheOnStart = CursorUtil.getColumnIndexOrThrow(_cursor, "clearCacheOnStart");
          final int _cursorIndexOfTimeoutSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "timeoutSeconds");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final ProfileEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final String _tmpUrlsJson;
            _tmpUrlsJson = _cursor.getString(_cursorIndexOfUrlsJson);
            final int _tmpTabCount;
            _tmpTabCount = _cursor.getInt(_cursorIndexOfTabCount);
            final String _tmpExecutionMode;
            _tmpExecutionMode = _cursor.getString(_cursorIndexOfExecutionMode);
            final int _tmpRefreshIntervalSec;
            _tmpRefreshIntervalSec = _cursor.getInt(_cursorIndexOfRefreshIntervalSec);
            final int _tmpScrollIntervalSec;
            _tmpScrollIntervalSec = _cursor.getInt(_cursorIndexOfScrollIntervalSec);
            final boolean _tmpRandomizeIntervals;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfRandomizeIntervals);
            _tmpRandomizeIntervals = _tmp != 0;
            final int _tmpMinRefreshIntervalSec;
            _tmpMinRefreshIntervalSec = _cursor.getInt(_cursorIndexOfMinRefreshIntervalSec);
            final int _tmpMaxRefreshIntervalSec;
            _tmpMaxRefreshIntervalSec = _cursor.getInt(_cursorIndexOfMaxRefreshIntervalSec);
            final int _tmpMinScrollIntervalSec;
            _tmpMinScrollIntervalSec = _cursor.getInt(_cursorIndexOfMinScrollIntervalSec);
            final int _tmpMaxScrollIntervalSec;
            _tmpMaxScrollIntervalSec = _cursor.getInt(_cursorIndexOfMaxScrollIntervalSec);
            final String _tmpCustomScript;
            if (_cursor.isNull(_cursorIndexOfCustomScript)) {
              _tmpCustomScript = null;
            } else {
              _tmpCustomScript = _cursor.getString(_cursorIndexOfCustomScript);
            }
            final String _tmpAllowedDomainsJson;
            _tmpAllowedDomainsJson = _cursor.getString(_cursorIndexOfAllowedDomainsJson);
            final String _tmpUserAgent;
            if (_cursor.isNull(_cursorIndexOfUserAgent)) {
              _tmpUserAgent = null;
            } else {
              _tmpUserAgent = _cursor.getString(_cursorIndexOfUserAgent);
            }
            final boolean _tmpClearCookiesOnStart;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfClearCookiesOnStart);
            _tmpClearCookiesOnStart = _tmp_1 != 0;
            final boolean _tmpClearCacheOnStart;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfClearCacheOnStart);
            _tmpClearCacheOnStart = _tmp_2 != 0;
            final int _tmpTimeoutSeconds;
            _tmpTimeoutSeconds = _cursor.getInt(_cursorIndexOfTimeoutSeconds);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            _result = new ProfileEntity(_tmpId,_tmpName,_tmpDescription,_tmpUrlsJson,_tmpTabCount,_tmpExecutionMode,_tmpRefreshIntervalSec,_tmpScrollIntervalSec,_tmpRandomizeIntervals,_tmpMinRefreshIntervalSec,_tmpMaxRefreshIntervalSec,_tmpMinScrollIntervalSec,_tmpMaxScrollIntervalSec,_tmpCustomScript,_tmpAllowedDomainsJson,_tmpUserAgent,_tmpClearCookiesOnStart,_tmpClearCacheOnStart,_tmpTimeoutSeconds,_tmpCreatedAt,_tmpUpdatedAt,_tmpStatus);
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
  public Flow<ProfileEntity> getByIdFlow(final String id) {
    final String _sql = "SELECT * FROM automation_profiles WHERE id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"automation_profiles"}, new Callable<ProfileEntity>() {
      @Override
      @Nullable
      public ProfileEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfUrlsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "urlsJson");
          final int _cursorIndexOfTabCount = CursorUtil.getColumnIndexOrThrow(_cursor, "tabCount");
          final int _cursorIndexOfExecutionMode = CursorUtil.getColumnIndexOrThrow(_cursor, "executionMode");
          final int _cursorIndexOfRefreshIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "refreshIntervalSec");
          final int _cursorIndexOfScrollIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "scrollIntervalSec");
          final int _cursorIndexOfRandomizeIntervals = CursorUtil.getColumnIndexOrThrow(_cursor, "randomizeIntervals");
          final int _cursorIndexOfMinRefreshIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "minRefreshIntervalSec");
          final int _cursorIndexOfMaxRefreshIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "maxRefreshIntervalSec");
          final int _cursorIndexOfMinScrollIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "minScrollIntervalSec");
          final int _cursorIndexOfMaxScrollIntervalSec = CursorUtil.getColumnIndexOrThrow(_cursor, "maxScrollIntervalSec");
          final int _cursorIndexOfCustomScript = CursorUtil.getColumnIndexOrThrow(_cursor, "customScript");
          final int _cursorIndexOfAllowedDomainsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "allowedDomainsJson");
          final int _cursorIndexOfUserAgent = CursorUtil.getColumnIndexOrThrow(_cursor, "userAgent");
          final int _cursorIndexOfClearCookiesOnStart = CursorUtil.getColumnIndexOrThrow(_cursor, "clearCookiesOnStart");
          final int _cursorIndexOfClearCacheOnStart = CursorUtil.getColumnIndexOrThrow(_cursor, "clearCacheOnStart");
          final int _cursorIndexOfTimeoutSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "timeoutSeconds");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final ProfileEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final String _tmpUrlsJson;
            _tmpUrlsJson = _cursor.getString(_cursorIndexOfUrlsJson);
            final int _tmpTabCount;
            _tmpTabCount = _cursor.getInt(_cursorIndexOfTabCount);
            final String _tmpExecutionMode;
            _tmpExecutionMode = _cursor.getString(_cursorIndexOfExecutionMode);
            final int _tmpRefreshIntervalSec;
            _tmpRefreshIntervalSec = _cursor.getInt(_cursorIndexOfRefreshIntervalSec);
            final int _tmpScrollIntervalSec;
            _tmpScrollIntervalSec = _cursor.getInt(_cursorIndexOfScrollIntervalSec);
            final boolean _tmpRandomizeIntervals;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfRandomizeIntervals);
            _tmpRandomizeIntervals = _tmp != 0;
            final int _tmpMinRefreshIntervalSec;
            _tmpMinRefreshIntervalSec = _cursor.getInt(_cursorIndexOfMinRefreshIntervalSec);
            final int _tmpMaxRefreshIntervalSec;
            _tmpMaxRefreshIntervalSec = _cursor.getInt(_cursorIndexOfMaxRefreshIntervalSec);
            final int _tmpMinScrollIntervalSec;
            _tmpMinScrollIntervalSec = _cursor.getInt(_cursorIndexOfMinScrollIntervalSec);
            final int _tmpMaxScrollIntervalSec;
            _tmpMaxScrollIntervalSec = _cursor.getInt(_cursorIndexOfMaxScrollIntervalSec);
            final String _tmpCustomScript;
            if (_cursor.isNull(_cursorIndexOfCustomScript)) {
              _tmpCustomScript = null;
            } else {
              _tmpCustomScript = _cursor.getString(_cursorIndexOfCustomScript);
            }
            final String _tmpAllowedDomainsJson;
            _tmpAllowedDomainsJson = _cursor.getString(_cursorIndexOfAllowedDomainsJson);
            final String _tmpUserAgent;
            if (_cursor.isNull(_cursorIndexOfUserAgent)) {
              _tmpUserAgent = null;
            } else {
              _tmpUserAgent = _cursor.getString(_cursorIndexOfUserAgent);
            }
            final boolean _tmpClearCookiesOnStart;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfClearCookiesOnStart);
            _tmpClearCookiesOnStart = _tmp_1 != 0;
            final boolean _tmpClearCacheOnStart;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfClearCacheOnStart);
            _tmpClearCacheOnStart = _tmp_2 != 0;
            final int _tmpTimeoutSeconds;
            _tmpTimeoutSeconds = _cursor.getInt(_cursorIndexOfTimeoutSeconds);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            _result = new ProfileEntity(_tmpId,_tmpName,_tmpDescription,_tmpUrlsJson,_tmpTabCount,_tmpExecutionMode,_tmpRefreshIntervalSec,_tmpScrollIntervalSec,_tmpRandomizeIntervals,_tmpMinRefreshIntervalSec,_tmpMaxRefreshIntervalSec,_tmpMinScrollIntervalSec,_tmpMaxScrollIntervalSec,_tmpCustomScript,_tmpAllowedDomainsJson,_tmpUserAgent,_tmpClearCookiesOnStart,_tmpClearCacheOnStart,_tmpTimeoutSeconds,_tmpCreatedAt,_tmpUpdatedAt,_tmpStatus);
          } else {
            _result = null;
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
