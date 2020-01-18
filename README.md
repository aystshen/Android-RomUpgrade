# Android 系统 OTA 升级应用
这是一个负责 Android 系统 ota 升级的后台应用，开机后自动运行后台Service，支持系统升级和应用升级，支持本地升级（tf卡、u盘）和在线升级（百度），支持推荐升级和静默升级。

已知兼容版本：
- Android 5.1
- Android 6.0
- Android 7.1
- Android 8.1

## 预览
### 本地升级（tf卡、u盘）
![](screenshots/device-2020-01-18-150651.png)

### 在线升级（[百度](https://ota.baidu.com/)）
![](screenshots/device-2020-01-18-150533.png)  

![](screenshots/device-2020-01-18-150622.png)

## 集成
这里讲述如何将此升级应用内置到您定制的系统固件中。

### 前提条件
- 系统签名
- root权限（应用升级默认采用静默安装，因此需要root权限）

### 内置
1. 编译release版本apk文件（或者直接下载已发布的release版本）。
2. 在Android源码vendor/xxx/common/apps/路径下新建“RomUpgrade”目录。
3. 将升级应用apk文件复制到“RomUpgrade”目录，并重命名为“RomUpgrade.apk。
4. 将升级应用apk文件中的so库提取出来，复制到“RomUpgrade/lib/arm/”（如果是64系统请提取64库到“RomUpgrade/lib/arm64/”）。
5. 新建Android.mk文件，内容如下：

```
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := RomUpgrade
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_TAGS := optional
LOCAL_BUILT_MODULE_STEM := package.apk
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
#LOCAL_PRIVILEGED_MODULE :=
LOCAL_CERTIFICATE := PRESIGNED
#LOCAL_OVERRIDES_PACKAGES := 
LOCAL_SRC_FILES := $(LOCAL_MODULE).apk
#LOCAL_REQUIRED_MODULES :=
LOCAL_PREBUILT_JNI_LIBS := \
		lib/arm/libotaso.so \
		lib/arm/libnative-lib.so
include $(BUILD_PREBUILT)

```
6. 修改vendor/xxx/common/apps/apps.mk，如下：

```
PRODUCT_PACKAGES += \
    RomUpgrade

```

### 配置属性
将下面属性配置到系统build.prop中：
```
# 百度ota平台产品线id
ro.baidu.product.id=10254

# 百度ota平台产品线密钥
ro.baidu.product.secret=NTUyOGFhOTVjODRlZjFmOA==

# 可升级的内置应用包名（多个包名通过逗号分隔，无应用升级可不配）
ro.baidu.presetapp=com.ayst.sample1,com.ayst.sample2
```

## 使用

### 本地升级（tf卡、u盘）
#### 本地应用升级
1. 在tf卡或u盘根目录新建“exupdate”目录。
2. 将待安装apk文件复制到“exupdate”目录下。
3. 插入tf卡或u盘插入Android设备。
4. 等待5秒左右，会弹出升级提示对话框，请根据提示完成升级。

#### 本地系统升级
1. 在tf卡或u盘根目录新建“exupdate”目录。
2. 将待升级系统ota包复制到“exupdate”目录下，并重命名为“update.zip”。
3. 插入tf卡或u盘插入Android设备。
4. 等待5秒左右，会弹出升级提示对话框，请根据提示完成升级。

#### 本地应用与系统同时升级
1. 在tf卡或u盘根目录新建“exupdate”目录。
2. 将待安装apk文件复制到“exupdate”目录下。
3. 将待升级系统ota包复制到“exupdate”目录下，并重命名为“update.zip”。
4. 插入tf卡或u盘插入Android设备。
5. 等待5秒左右，会弹出升级提示对话框，请根据提示完成升级。

### 在线升级（百度）
1. 登录 <https://ota.baidu.com/>，进入控制台。
2. 进入对应产品线，根据提示部署升级（可以同时部署系统与应用升级）。
3. Android设备在每次重启有网络情况下会查询一次升级，之后每30分钟会查询一次。
4. 如果查询到待升级版本，将弹出升级提示对话框，按提示操作即可完成升级。

## 开发者
* ayst.shen@foxmail.com

## License
```
Copyright 2019 Bob Shen

Licensed under the Apache License, Version 2.0 (the "License"); you may 
not use this file except in compliance with the License. You may obtain 
a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software 
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT 
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
License for the specific language governing permissions and limitations 
under the License.
```