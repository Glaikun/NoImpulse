package com.glaikun.noimpulse.api

data class StatusSnapshot(val usageGranted: Boolean,
                          val isDefaultHome: Boolean,
                          val usage: DailyUsage?)
