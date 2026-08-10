# location-aware-distance-recommendation Specification

## Purpose
TBD - created by archiving change normalize-user-location. Update Purpose after archive.
## Requirements
### Requirement: 距离上下文只保存允许用于个人距离的位置

`DistanceContextService` MUST 通过统一位置适配器校验浏览器坐标；仅 DEVICE、POI、ADDRESS 坐标可以创建或更新距离上下文。距离结果必须标注为直线距离。

#### Scenario: 使用设备位置建立距离上下文

- **WHEN** C 上传合法浏览器坐标
- **THEN** 系统仅在当前进程的 TTL 内保存设备坐标并允许距离排序

#### Scenario: 使用城市代表点建立距离上下文

- **WHEN** 调用方试图以 CITY 或 DISTRICT 粒度的位置创建距离上下文
- **THEN** 系统拒绝该操作，且不覆盖已有的设备位置

