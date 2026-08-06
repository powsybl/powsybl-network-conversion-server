# PowSyBl Network Conversion Server

[![Actions Status](https://github.com/powsybl/powsybl-network-conversion-server/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/powsybl/powsybl-network-conversion-server/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=com.powsybl%3Apowsybl-network-conversion-server&metric=coverage)](https://sonarcloud.io/component_measures?id=com.powsybl%3Apowsybl-network-conversion-server&metric=coverage)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)
[![Slack](https://img.shields.io/badge/slack-powsybl-blueviolet.svg?logo=slack)](https://join.slack.com/t/powsybl/shared_invite/zt-36jvd725u-cnquPgZb6kpjH8SKh~FWHQ)

## Description

The **powsybl-network-conversion-server** is a microservice of the [GridSuite](https://github.com/gridsuite) platform dedicated to **importing cases into the network store and exporting networks back to case files**.

It provides the following capabilities:

- **Import a case asynchronously**: read a case from the case-server datasource API, convert it to IIDM using PowSyBl importers (CGMES, UCTE, XIIDM, Matpower, IEEE-CDF, …), persist it in the network-store, index equipment in Elasticsearch, and post functional logs to the report-server — all in parallel.
- **Import a CGMES case with custom boundaries**: override the boundary files used during CGMES import with caller-provided boundary content.
- **Export a network asynchronously**: read a network variant from the network-store, convert it to the requested format with optional ZIP or GZIP compression, upload the result to S3, and notify the caller when done.
- **Export a case asynchronously**: convert a case file already stored in the case-server directly to a new format (without going through the network store), upload the result to S3, and notify the caller.
- **Download an export file**: stream a previously exported file from S3 back to the caller.
- **Index equipment in Elasticsearch**: on import, index all equipment types relevant to the network map (substations, voltage levels, lines, transformers, generators, loads, …). Supports full reindexation per network including all variants (created, modified, tombstoned equipment).
- **List available import/export formats and parameters**: expose the PowSyBl importer/exporter registry with their functional parameters.

---

## Technical Stack

- Spring Boot (Web, Actuator)
- Amazon S3 (AWS SDK v2) via `spring-cloud-aws-starter-s3` (export file storage)
- Elasticsearch (`spring-data-elasticsearch`, equipment indexation)
- RabbitMQ via Spring Cloud Stream (async import/export pipeline)
- PowSyBl importers: CGMES, UCTE, XIIDM, Matpower, IEEE-CDF
- `powsybl-network-store-client` (network persistence)
- `powsybl-case-datasource-client` (case file reading)
- API documentation: OpenAPI / Swagger (`springdoc`)
- Micrometer / Prometheus

---

## Development Scripts

Build Docker image:

```shell
mvn install -DskipTests -Dpowsybl.docker.install
```

---

## Asynchronous Import Flow

1. The caller sends `POST /v1/networks` — the server publishes a message on the `caseImportStart` queue.
2. Parallel consumers (`consumeCaseImportStart1`, `consumeCaseImportStart2`) pick up messages for load balancing.
3. The case is read from case-server via the datasource API, parsed with the appropriate PowSyBl importer, and stored in the network-store. Equipment indexation and report sending are executed in parallel.
4. A `caseImportSucceeded` message is published with the resulting `networkUuid` and `networkId`.

## Asynchronous Export Flow

1. The caller sends `POST /v1/networks/{networkUuid}/export` (network) or `POST /v1/cases/{caseUuid}/export` (case) — the server publishes a message on `networkExportStart` or `caseExportStart`.
2. Parallel consumers process the export, produce a ZIP or GZIP file in a temp directory, upload it to S3, and publish a `networkExportFinished` or `caseExportFinished` message.
3. The caller can then download the file via `GET /v1/export/results/{exportUuid}`.

---

## Interactions with Other Microservices

```text
┌──────────────────────────────────────┐
│  powsybl-network-conversion-server   │──► case-server       (read case files via datasource API)
│                                      │──► network-store-server (persist/read network variants)
│                                      │──► report-server      (post import functional logs)
└──────────────────────────────────────┘
             ▲  ▼
          RabbitMQ
            caseImportStart / caseImportSucceeded
            networkExportStart / networkExportFinished
            caseExportStart / caseExportFinished
```

---

## Micrometer Observability

Key operations are wrapped in named Micrometer observations via `NetworkConversionObserver`:

---

## Useful Links

- [PowSyBl supported formats](https://www.powsybl.org/pages/documentation/index.html#grid-formats)
