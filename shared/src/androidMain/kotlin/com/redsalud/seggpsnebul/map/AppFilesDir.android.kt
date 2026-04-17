package com.redsalud.seggpsnebul.map

import com.redsalud.seggpsnebul.location.DeviceIdProvider

actual fun appFilesDir(): String =
    DeviceIdProvider.getAppContext().filesDir.absolutePath
