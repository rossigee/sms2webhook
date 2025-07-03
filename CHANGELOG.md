# Changelog

## [2.0.0] - 2024-01-03

### 🎨 UI/UX Improvements
- Complete Material Design 3 overhaul with modern components
- New card-based layout with better visual hierarchy
- Added dark mode support
- Improved statistics display with color-coded metrics
- RecyclerView-based activity log with timestamps and status icons
- Pull-to-refresh functionality
- Responsive design that adapts to all screen sizes

### 🏗️ Architecture
- Implemented MVVM architecture with ViewModels and LiveData
- Proper separation of concerns
- Background operations moved off main thread
- Added proper lifecycle management

### ✨ New Features
- Test Connection button in settings to verify webhook configuration
- Diagnostic system to detect cache inconsistencies
- Real-time input validation for webhook URL
- Swipe refresh for activity logs
- Material Design buttons and progress indicators
- Improved error messages and user feedback

### 🐛 Bug Fixes
- **Fixed cache inconsistency issue** where duplicate entries caused incorrect counts
- Database now uses message hash as primary key to prevent duplicates
- Added database migration to clean up existing duplicates
- Fixed potential memory leaks with proper lifecycle handling
- Resolved threading issues with database operations

### 🔧 Technical Improvements
- Upgraded to Room database version 2 with migration
- Added AndroidX lifecycle components
- Implemented proper dependency injection patterns
- Updated all dependencies to latest stable versions
- Added comprehensive GitHub Actions CI/CD

### 📝 Database Changes
- **Migration Required**: Database schema updated from v1 to v2
- Primary key changed from auto-increment ID to message hash
- Automatic cleanup of duplicate entries during migration
- INSERT OR REPLACE strategy to prevent future duplicates

### 🚀 Performance
- Optimized log display with RecyclerView
- Limited log entries to prevent memory issues
- Improved database query efficiency
- Better resource management

### 📦 Dependencies Updated
- Material Design Components
- AndroidX Lifecycle
- Room Database
- RecyclerView
- SwipeRefreshLayout

---

## [1.3.1] - Previous Release
- Basic SMS to webhook functionality
- Simple UI with basic statistics
- Cache system for tracking sent messages