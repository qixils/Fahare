# Changelog

## [Unreleased]

### Changed

- Player inventories, ender chests, and XP are now cleared on reset
- The server should no longer attempt multiple resets at the same time

## [1.1.0] - 2023-08-21

### Added

- Configuration file with various settings
- `/fahare reset` command to manually reset the world (requires `fahare.reset` permission)
- Optional config setting to disable the automatic reset feature in favor of the reset command
- Optional config setting to reset the world on *any* player death, not just when all players die

### Changed

- Deleted worlds are now backed up in the `fahare-backups` folder by default (configurable)

## [1.0.1] - 2023-08-07

### Added

- MIT license

### Changed

- Made more log messages translatable
- De-duplicated error logs
- Switched log messages to use the world short name instead of the key to match vanilla logs

## [1.0.0] - 2023-08-06

### Added

- Automatic world reset when all online players die
- Support for Paper 1.19.3+
- English translation

[unreleased]: https://github.com/qixils/fahare/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/qixils/fahare/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/qixils/fahare/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/qixils/fahare/releases/tag/v1.0.0