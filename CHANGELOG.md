# Changelog

## [unreleased] - 2025-08-31

### Added

- Newly generated worlds log their seeds to console

## [1.2.1] - 2024-07-22

### Changed

- Health and hunger are now reset for all online players when starting a new world (#1)
- The difficulty of the original overworld is now copied onto the newly generated world (#3)

## [1.2.0] - 2024-01-27

### Added

- New config option "lives" which specifies how many deaths each player is allowed

### Changed

- Spectators no longer inherently count as dead players

### Fixed

- Removed a rare freeze on reset by forcefully loading and recreating the fake overworld

## [1.1.1] - 2023-09-17

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

[unreleased]: https://github.com/qixils/fahare/compare/v1.2.1...HEAD
[1.2.1]: https://github.com/qixils/fahare/compare/v1.2.0...v1.2.1
[1.2.0]: https://github.com/qixils/fahare/compare/v1.1.1...v1.2.0
[1.1.1]: https://github.com/qixils/fahare/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/qixils/fahare/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/qixils/fahare/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/qixils/fahare/releases/tag/v1.0.0