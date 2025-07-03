# Cache Inconsistency Fix

## The Problem

Your cache was showing inconsistent counts:
- Store: 349 messages
- Sent: 491 messages  
- Unsent: 176 messages

The math doesn't add up: 491 + 176 = 667, which is much more than the 349 messages in your SMS store.

## Root Cause

The cache database had a fundamental design flaw:

1. **Duplicate Entries**: The `CacheEntry` table used an auto-incrementing ID as the primary key, but the message hash (`key`) was NOT unique. This allowed multiple entries for the same message.

2. **How Duplicates Occurred**:
   - When the app retries failed uploads, it creates new cache entries
   - App restarts/crashes during processing could create duplicates
   - The same message processed multiple times gets multiple cache entries

3. **Counting Issue**: The sent/unsent queries counted ALL rows, including duplicates, while the store count is the actual number of unique messages.

## The Fix

### 1. Database Schema Update
- Changed the primary key from auto-increment ID to the message hash (`key`)
- Added `INSERT OR REPLACE` strategy to prevent duplicates
- Created migration to clean up existing duplicates (keeps only the latest status for each message)

### 2. Diagnostic Features
- Added diagnostic logging to detect inconsistencies
- Shows duplicate count and missing entries in the activity log
- Helps identify when the cache gets out of sync

### 3. UI Improvements
- Clear Cache dialog now offers "Fix Duplicates" option
- Better error messages and status reporting
- Shows warnings when inconsistencies are detected

## What Happens Next

When you install the updated app:

1. **Automatic Migration**: On first launch, the database will migrate to version 2, removing all duplicate entries automatically.

2. **Accurate Counts**: You should see:
   - Store: 349 (actual SMS count)
   - Sent + Unsent = 349 (or less if some messages were never processed)

3. **No More Duplicates**: Future operations will update existing entries instead of creating duplicates.

## Recommendations

1. **After Update**: 
   - Check the activity log for diagnostic messages
   - The counts should now add up correctly
   - If needed, use "Clear Cache" and re-sync to start fresh

2. **Going Forward**:
   - The app will maintain consistency automatically
   - Retries and restarts won't create duplicates
   - Each message will have exactly one cache entry

## Technical Details

- Database version upgraded from 1 to 2
- Migration SQL keeps only the latest entry for each message hash
- Uses Room's `OnConflictStrategy.REPLACE` for upserts
- Added `getUniqueCount()` and `getTotalCount()` queries for diagnostics