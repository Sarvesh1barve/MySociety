# Backup and restore

Back up PostgreSQL **and** uploaded files together. Database metadata alone cannot restore documents.

## Backup

Stop new writes during a coordinated backup, then run:

```powershell
.\scripts\backup.ps1
```

A new timestamp-and-random-suffix directory is created beneath .local/backups (or a supplied OutputDirectory). It contains database.dump and an uploads directory if uploads exist. Existing backups are never overwritten.

The script uses pg_dump custom format, the configured datasource connection and a process-scoped PGPASSWORD. No password is passed in command-line arguments or printed. Protect backups like resident data; store a separate encrypted copy off the laptop.

Equivalent command after setting local connection variables:

```text
pg_dump -h 127.0.0.1 -p 5433 -U mysociety -d mysociety -Fc -f database.dump
```

## Restore without overwriting live data

```powershell
.\scripts\restore.ps1 -BackupDirectory 'D:\MySociety\.local\backups\<backup>' -NewDatabase mysociety_restore_verify
```

The script creates a **new** database whose name starts with mysociety_restore_. If that database exists, it stops. Files restore into a separate .local/<database>-uploads directory. The original database and upload directory are untouched.

Verify the restored database and file count, then deliberately change SPRING_DATASOURCE_URL and UPLOAD_DIR for the application if the restored copy should become active. Keep the old copy until verification is complete.

Test a restore periodically. Retention/deletion is manual in this MVP; the script does not remove old backups automatically.
