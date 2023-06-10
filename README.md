# TraceX
[![](https://img.shields.io/gradle-plugin-portal/v/io.github.b7woreo.tracex)](https://plugins.gradle.org/plugin/io.github.b7woreo.tracex)

TraceX 在编译时进行字节码插桩操作, 在所有方法的 __入口/出口__ 插入 `Trace#beginSection`/`Trace#endSection` 调用, 用于分析 android 应用性能.

## 使用方法

### 引入插件

在 `com.android.application` 项目的 `build.gradle` 文件中加入以下配置信息: 

```
plugins {
    id 'com.android.application' version '8.0.0'
    id 'io.github.b7woreo.tracex' version '<latest-version>'
}

android {
    // 省略...

    buildTypes {
        profile {
            initWith release
            
            // 在 profile 构建类型中启用 TraceX
            tracex {
                enable = true
            }
        }
    }
}
```

### 采集数据

参考 [Quickstart: Record traces on Android](https://perfetto.dev/docs/quickstart/android-tracing) 使用 perfetto 采集数据.