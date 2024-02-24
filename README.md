# TraceX
[![](https://img.shields.io/gradle-plugin-portal/v/io.github.b7woreo.tracex)](https://plugins.gradle.org/plugin/io.github.b7woreo.tracex)
 
1. 编译时通过字节码插桩操作, 在所有方法的 __入口/出口__ 插入 `Trace#beginSection`/`Trace#endSection` 调用, 用于分析 android 应用性能.
2. 支持通过正则表达式配置 include / exclude 方法列表, 控制 Trace 插桩范围.
3. 基于 artifact API 进行字节码操作, 拥有最优的增量构建性能.

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

### 配置范围

通过 `include` / `exclude` 可以添加正则表达式, 当正则表达式与 `TraceTag` 匹配时则会 进行插桩 / 禁用插桩.

建议针对调用栈较深且已知非耗时函数配置 `exclude` 规则, 否则可能会由于 `Trace` 本身的性能开销影响了分析结果 ,例如: 排除对 Kotlin 协程相关函数的插桩.

- `TraceTag` 格式: ${ClassName}#${MethodName}


```
android {
    // 省略...

    buildTypes {
        profile {
            tracex {
                // 省略...
            
                // 只对 com.example 包名的类进行插桩
                include(
                    "com.example.*",
                )
                
                // 不对 kotlinx.coroutines 包名的类进行插桩
                exclude(
                    "kotlinx.coroutines.*",
                )
            }
        }
    }
}
```

### 采集数据

参考 [Quickstart: Record traces on Android](https://perfetto.dev/docs/quickstart/android-tracing) 使用 perfetto 采集数据.