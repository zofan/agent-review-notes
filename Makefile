SHELL := /usr/bin/bash
.SHELLFLAGS := -eu -o pipefail -c
.DEFAULT_GOAL := help

GRADLEW ?= ./gradlew
IDE_JBR ?= $(HOME)/.local/share/JetBrains/Toolbox/apps/intellij-idea/jbr
ifeq ($(strip $(JAVA_HOME)),)
JAVA_HOME := $(IDE_JBR)
endif
export JAVA_HOME
export PATH := $(JAVA_HOME)/bin:$(PATH)

.PHONY: help check-env test test-kotlin test-python build rebuild verify verify-config check release run clean tasks artifacts stop-gradle check-diff

help: ## Показать доступные команды
	@printf '%s\n' \
		'Agent Review Notes' \
		'' \
		'  make build         Собрать ZIP плагина без запуска тестов' \
		'  make rebuild       Очистить build/ и заново собрать ZIP' \
		'  make test          Запустить Kotlin- и Python-тесты' \
		'  make test-kotlin   Запустить только Kotlin-тесты' \
		'  make test-python   Запустить только тесты bundled CLI' \
		'  make verify        Проверить ZIP через Plugin Verifier' \
		'  make verify-config Проверить конфигурацию плагина' \
		'  make check         Запустить тесты, проверки и diff-check' \
		'  make release       Чистая сборка со всеми release-gates' \
		'  make run           Запустить плагин в sandbox IDE' \
		'  make artifacts     Показать SHA-256 собранных ZIP' \
		'  make tasks         Показать задачи Gradle' \
		'  make clean         Удалить результаты сборки' \
		'  make stop-gradle   Остановить Gradle daemon'

check-env: ## Проверить Gradle Wrapper и JBR
	@test -x "$(GRADLEW)" || { printf 'Ошибка: не найден исполняемый %s\n' "$(GRADLEW)" >&2; exit 1; }
	@test -x "$(JAVA_HOME)/bin/java" || { printf 'Ошибка: JBR не найден в %s; задайте JAVA_HOME или IDE_JBR\n' "$(JAVA_HOME)" >&2; exit 1; }
	@"$(JAVA_HOME)/bin/java" -version

# Основные команды намеренно не используют --rerun-tasks: обычный локальный цикл остаётся быстрым.
test: check-env ## Запустить все тесты
	@$(MAKE) --no-print-directory test-kotlin
	@$(MAKE) --no-print-directory test-python

test-kotlin: check-env ## Запустить Kotlin-тесты
	$(GRADLEW) test

test-python: ## Запустить тесты bundled Python CLI
	PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s skills/agent-review-notes/tests -p 'test_*.py'

build: check-env ## Собрать ZIP плагина без тестов
	$(GRADLEW) buildPlugin -x test

rebuild: check-env ## Выполнить чистую сборку ZIP без тестов
	$(GRADLEW) clean
	$(GRADLEW) buildPlugin -x test

verify: check-env ## Запустить Plugin Verifier
	$(GRADLEW) verifyPlugin -x test

verify-config: check-env ## Проверить конфигурацию плагина
	$(GRADLEW) verifyPluginProjectConfiguration

check-diff: ## Проверить whitespace в незакоммиченном diff
	git diff --check

check: check-env ## Запустить тесты и обязательные проверки
	@$(MAKE) --no-print-directory test
	$(GRADLEW) verifyPluginProjectConfiguration verifyPlugin
	@$(MAKE) --no-print-directory check-diff

release: check-env ## Выполнить чистую проверенную release-сборку
	$(GRADLEW) clean
	PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s skills/agent-review-notes/tests -p 'test_*.py'
	$(GRADLEW) test buildPlugin verifyPluginProjectConfiguration verifyPlugin --rerun-tasks
	@$(MAKE) --no-print-directory artifacts

run: check-env ## Запустить sandbox IDE
	$(GRADLEW) runIde

clean: check-env ## Очистить результаты сборки
	$(GRADLEW) clean

tasks: check-env ## Показать задачи Gradle
	$(GRADLEW) tasks

artifacts: ## Показать SHA-256 всех собранных ZIP
	@found=0; \
	for artifact in build/distributions/*.zip; do \
		if [[ ! -e "$$artifact" ]]; then continue; fi; \
		found=1; \
		sha256sum "$$artifact"; \
	done; \
	if [[ "$$found" -eq 0 ]]; then \
		printf 'Ошибка: ZIP не найден; сначала выполните make build\n' >&2; \
		exit 1; \
	fi

stop-gradle: check-env ## Остановить Gradle daemon
	$(GRADLEW) --stop
