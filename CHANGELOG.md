# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project follows Semantic Versioning.

## [Unreleased]

### Added

- Open-source baseline package:
  - `README.md`
  - `LICENSE` (MIT)
  - `.env.example` and `web/.env.example`
  - GitHub Actions CI workflow (`.github/workflows/ci.yml`)
  - `CONTRIBUTING.md`
  - `SECURITY.md`
  - Issue/PR templates

### Changed

- Removed internal Maven repository configuration from `pom.xml` to make public builds reproducible with public repositories.
