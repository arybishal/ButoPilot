package com.autopilot.android.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.autopilot.android.data.local.dao.ExecutionHistoryDao;
import com.autopilot.android.data.local.dao.ExecutionHistoryDao_Impl;
import com.autopilot.android.data.local.dao.ProfileDao;
import com.autopilot.android.data.local.dao.ProfileDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AutoPilotDatabase_Impl extends AutoPilotDatabase {
  private volatile ProfileDao _profileDao;

  private volatile ExecutionHistoryDao _executionHistoryDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `automation_profiles` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `urlsJson` TEXT NOT NULL, `tabCount` INTEGER NOT NULL, `executionMode` TEXT NOT NULL, `refreshIntervalSec` INTEGER NOT NULL, `scrollIntervalSec` INTEGER NOT NULL, `randomizeIntervals` INTEGER NOT NULL, `minRefreshIntervalSec` INTEGER NOT NULL, `maxRefreshIntervalSec` INTEGER NOT NULL, `minScrollIntervalSec` INTEGER NOT NULL, `maxScrollIntervalSec` INTEGER NOT NULL, `customScript` TEXT, `allowedDomainsJson` TEXT NOT NULL, `userAgent` TEXT, `clearCookiesOnStart` INTEGER NOT NULL, `clearCacheOnStart` INTEGER NOT NULL, `timeoutSeconds` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `execution_history` (`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, `profileName` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `durationSeconds` INTEGER NOT NULL, `totalActions` INTEGER NOT NULL, `status` TEXT NOT NULL, `errorMessage` TEXT, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'fc10648cc32a536dc391da6cc0dc0a77')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `automation_profiles`");
        db.execSQL("DROP TABLE IF EXISTS `execution_history`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsAutomationProfiles = new HashMap<String, TableInfo.Column>(22);
        _columnsAutomationProfiles.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutomationProfiles.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutomationProfiles.put("description", new TableInfo.Column("description", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutomationProfiles.put("urlsJson", new TableInfo.Column("urlsJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutomationProfiles.put("tabCount", new TableInfo.Column("tabCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutomationProfiles.put("executionMode", new TableInfo.Column("executionMode", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutomationProfiles.put("refreshIntervalSec", new TableInfo.Column("refreshIntervalSec", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutomationProfiles.put("scrollIntervalSec", new TableInfo.Column("scrollIntervalSec", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutomationProfiles.put("randomizeIntervals", new TableInfo.Column("randomizeIntervals", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutomationProfiles.put("minRefreshIntervalSec", new TableInfo.Column("minRefreshIntervalSec", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutomationProfiles.put("maxRefreshIntervalSec", new TableInfo.Column("maxRefreshIntervalSec", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutomationProfiles.put("minScrollIntervalSec", new TableInfo.Column("minScrollIntervalSec", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutomationProfiles.put("maxScrollIntervalSec", new TableInfo.Column("maxScrollIntervalSec", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutomationProfiles.put("customScript", new TableInfo.Column("customScript", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutomationProfiles.put("allowedDomainsJson", new TableInfo.Column("allowedDomainsJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutomationProfiles.put("userAgent", new TableInfo.Column("userAgent", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutomationProfiles.put("clearCookiesOnStart", new TableInfo.Column("clearCookiesOnStart", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutomationProfiles.put("clearCacheOnStart", new TableInfo.Column("clearCacheOnStart", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutomationProfiles.put("timeoutSeconds", new TableInfo.Column("timeoutSeconds", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutomationProfiles.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutomationProfiles.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutomationProfiles.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAutomationProfiles = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesAutomationProfiles = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoAutomationProfiles = new TableInfo("automation_profiles", _columnsAutomationProfiles, _foreignKeysAutomationProfiles, _indicesAutomationProfiles);
        final TableInfo _existingAutomationProfiles = TableInfo.read(db, "automation_profiles");
        if (!_infoAutomationProfiles.equals(_existingAutomationProfiles)) {
          return new RoomOpenHelper.ValidationResult(false, "automation_profiles(com.autopilot.android.data.local.entity.ProfileEntity).\n"
                  + " Expected:\n" + _infoAutomationProfiles + "\n"
                  + " Found:\n" + _existingAutomationProfiles);
        }
        final HashMap<String, TableInfo.Column> _columnsExecutionHistory = new HashMap<String, TableInfo.Column>(9);
        _columnsExecutionHistory.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExecutionHistory.put("profileId", new TableInfo.Column("profileId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExecutionHistory.put("profileName", new TableInfo.Column("profileName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExecutionHistory.put("startedAt", new TableInfo.Column("startedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExecutionHistory.put("endedAt", new TableInfo.Column("endedAt", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExecutionHistory.put("durationSeconds", new TableInfo.Column("durationSeconds", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExecutionHistory.put("totalActions", new TableInfo.Column("totalActions", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExecutionHistory.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsExecutionHistory.put("errorMessage", new TableInfo.Column("errorMessage", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysExecutionHistory = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesExecutionHistory = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoExecutionHistory = new TableInfo("execution_history", _columnsExecutionHistory, _foreignKeysExecutionHistory, _indicesExecutionHistory);
        final TableInfo _existingExecutionHistory = TableInfo.read(db, "execution_history");
        if (!_infoExecutionHistory.equals(_existingExecutionHistory)) {
          return new RoomOpenHelper.ValidationResult(false, "execution_history(com.autopilot.android.data.local.entity.ExecutionHistoryEntity).\n"
                  + " Expected:\n" + _infoExecutionHistory + "\n"
                  + " Found:\n" + _existingExecutionHistory);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "fc10648cc32a536dc391da6cc0dc0a77", "85d7955f1c924c2bde9ef819d5bc36cb");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "automation_profiles","execution_history");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `automation_profiles`");
      _db.execSQL("DELETE FROM `execution_history`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(ProfileDao.class, ProfileDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ExecutionHistoryDao.class, ExecutionHistoryDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public ProfileDao profileDao() {
    if (_profileDao != null) {
      return _profileDao;
    } else {
      synchronized(this) {
        if(_profileDao == null) {
          _profileDao = new ProfileDao_Impl(this);
        }
        return _profileDao;
      }
    }
  }

  @Override
  public ExecutionHistoryDao executionHistoryDao() {
    if (_executionHistoryDao != null) {
      return _executionHistoryDao;
    } else {
      synchronized(this) {
        if(_executionHistoryDao == null) {
          _executionHistoryDao = new ExecutionHistoryDao_Impl(this);
        }
        return _executionHistoryDao;
      }
    }
  }
}
