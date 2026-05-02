package com.pingra.app.sms

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent

class SmsRoleHelper(private val context: Context) {
    private val roleManager: RoleManager?
        get() = context.getSystemService(RoleManager::class.java)

    fun isSmsRoleAvailable(): Boolean {
        return roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true
    }

    fun isDefaultSmsApp(): Boolean {
        val manager = roleManager ?: return false
        return manager.isRoleAvailable(RoleManager.ROLE_SMS) && manager.isRoleHeld(RoleManager.ROLE_SMS)
    }

    fun createRequestRoleIntent(): Intent? {
        val manager = roleManager ?: return null
        if (!manager.isRoleAvailable(RoleManager.ROLE_SMS)) return null
        if (manager.isRoleHeld(RoleManager.ROLE_SMS)) return null
        return manager.createRequestRoleIntent(RoleManager.ROLE_SMS)
    }
}

