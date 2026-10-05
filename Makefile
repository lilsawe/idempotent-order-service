SHELL := /bin/bash
.PHONY: help run test verify coverage bench smoke docker-up docker-down clean

help:  ## 显示所有可用命令
	@grep -E '^[a-zA-Z_-]+:.*?## .*' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

run:  ## 本地启动（H2 内存库，零依赖）
	mvn spring-boot:run

run-mysql:  ## 用 MySQL + Redis 启动（需先 make docker-up）
	mvn spring-boot:run -Dspring-boot.run.profiles=mysql,redis

test:  ## 跑测试
	mvn -B -ntp test

verify:  ## 跑测试 + 覆盖率报告
	mvn -B -ntp verify

coverage: verify  ## 生成并打开覆盖率报告
	open target/site/jacoco/index.html

bench:  ## 压测（需先 make run）
	node benchmark/load-test.mjs

smoke:  ## 一键冒烟：下单 / 幂等 / 状态流转 / 对账（需先 make run）
	bash scripts/smoke.sh

docker-up:  ## 启动 MySQL + Redis
	docker compose up -d

docker-down:  ## 停止 MySQL + Redis
	docker compose down

clean:  ## 清理构建产物
	mvn -B -ntp clean
