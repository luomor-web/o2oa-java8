package net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.o2.security


import android.os.Bundle
import android.text.TextUtils
import android.widget.EditText
import kotlinx.android.synthetic.main.activity_account_security.*
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.*
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.base.BaseMVPActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.o2.bind.BindPhoneActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.o2.my.MyInfoActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.bo.api.im.MessageType
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.XLog
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.XToast
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.biometric.BioConstants
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.biometric.BiometryManager
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.biometric.OnBiometryAuthCallback
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.extension.*
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.widgets.dialog.O2DialogSupport


class AccountSecurityActivity : BaseMVPActivity<AccountSecurityContract.View, AccountSecurityContract.Presenter>(), AccountSecurityContract.View {
    override var mPresenter: AccountSecurityContract.Presenter = AccountSecurityPresenter()
    override fun layoutResId(): Int  = R.layout.activity_account_security
    override fun afterSetContentView(savedInstanceState: Bundle?) {
        mPresenter.getRSAPublicKey()
        setupToolBar(getString(R.string.title_activity_account_security), true)

        account_name_id.text = O2SDKManager.instance().cName
        account_change_mobile_label_id.text = O2SDKManager.instance().prefs().getString(O2.PRE_BIND_PHONE_KEY, "")

        if (BuildConfig.InnerServer) {
            account_change_mobile_id.inVisible()
        }else {
            account_change_mobile_id.visible()
            account_change_mobile_id.setOnClickListener { changeMobile() }
        }

        rl_account_security_name_btn.setOnClickListener {
            go<MyInfoActivity>()
        }

        rl_account_security_password_btn.setOnClickListener {
            changeMyPassword()
        }

        val unitHost = O2SDKManager.instance().prefs().getString(O2.PRE_CENTER_HOST_KEY, "")
        tv_account_security_unit_name.text = "????????????????????????$unitHost"

        initBiometryAuthView()


        if (BuildConfig.InnerServer) {
            ll_account_security_bind_device_layout.gone()
        }else {
            ll_account_security_bind_device_layout.visible()
            rl_account_security_bind_device_btn.setOnClickListener {
                go<DeviceManagerActivity>()
            }
        }

    }

    override fun logoutSuccess() {
        O2SDKManager.instance().logoutCleanCurrentPerson()
        O2SDKManager.instance().clearBindUnit()
        goAndClearBefore<BindPhoneActivity>()
    }

    override fun updateMyPasswordFail(message: String) {
        XToast.toastLong(this, message)
    }

    override fun updateMyPasswordSuccess() {
        XToast.toastShort(this, "?????????????????????")
    }

    private val bioManager: BiometryManager by lazy { BiometryManager(this) }
    private fun initBiometryAuthView() {

        tv_account_security_biometry_name.text = "??????????????????"
        val bioAuthUser = O2SDKManager.instance().prefs().getString(BioConstants.O2_bio_auth_user_id_prefs_key, "") ?: ""
        var isAuthed = false
        //???????????????????????????????????????
        if (bioAuthUser.isNotBlank()) {
            val array = bioAuthUser.split("^^")
            if (array.isNotEmpty() && array.size == 2) {
                val unitId = O2SDKManager.instance().prefs().getString(O2.PRE_BIND_UNIT_ID_KEY, "") ?: ""
                if (array[0] == unitId) {
                    isAuthed = true
                }
            }
        }

        if (isAuthed) {
            image_btn_account_security_biometry_enable.setImageResource(R.mipmap.icon_toggle_on_29dp)
        }else {
            image_btn_account_security_biometry_enable.setImageResource(R.mipmap.icon_toggle_off_29dp)
        }

        if (bioManager.isBiometricPromptEnable()) {
            image_btn_account_security_biometry_enable.setOnClickListener {
                bioManager.authenticate(object : OnBiometryAuthCallback{
                    override fun onUseFallBack() {
                        XLog.error("????????????????????????????????????????????????")
                    }

                    override fun onSucceeded() {
                        XLog.debug("????????????????????????")
                        XToast.toastShort(this@AccountSecurityActivity, "????????????")
                        setBioAuthResult()
                    }

                    override fun onFailed() {
                        XLog.error("??????????????????????????????????????????")
                        //XToast.toastShort(this@AccountSecurityActivity, "????????????")
                    }

                    override fun onError(code: Int, reason: String) {
                        XLog.error("???????????????????????????code:$code , reason:$reason")
                        //XToast.toastShort(this@AccountSecurityActivity, "???????????????$reason")
                    }

                    override fun onCancel() {
                        XLog.info("????????????????????????????????????")
                    }

                })
            }

        }else {
            tv_account_security_biometry_name.text = "???????????????????????????"
            image_btn_account_security_biometry_enable.setImageResource(R.mipmap.icon_toggle_off_29dp)
            image_btn_account_security_biometry_enable.setOnClickListener {
                XToast.toastShort(this, "????????????????????????????????????")
            }
        }
    }

    private fun changeMyPassword() {
        O2DialogSupport.openCustomViewDialog(this, "????????????", R.layout.dialog_password_modify) { dialog ->
            val old = dialog.findViewById<EditText>(R.id.dialog_password_old_edit_id).text.toString()
            if (TextUtils.isEmpty(old)) {
                XToast.toastShort(this@AccountSecurityActivity, "?????????????????????")
                return@openCustomViewDialog
            }
            val newpwd = dialog.findViewById<EditText>(R.id.dialog_password_new_edit_id).text.toString()
            if (TextUtils.isEmpty(newpwd)) {
                XToast.toastShort(this@AccountSecurityActivity, "?????????????????????")
                return@openCustomViewDialog
            }
            val newpwdAgain = dialog.findViewById<EditText>(R.id.dialog_password_confirm_edit_id).text.toString()
            if (newpwd != newpwdAgain) {
                XToast.toastShort(this@AccountSecurityActivity, "????????????????????????????????????")
                return@openCustomViewDialog
            }
            mPresenter.updateMyPassword(old, newpwd, newpwdAgain)
        }
    }

    //?????????????????? ????????????
    private fun setBioAuthResult() {
        val bioAuthUser = O2SDKManager.instance().prefs().getString(BioConstants.O2_bio_auth_user_id_prefs_key, "") ?: ""
        var isAuthed = false
        val unitId = O2SDKManager.instance().prefs().getString(O2.PRE_BIND_UNIT_ID_KEY, "") ?: ""
        //???????????????????????????????????????
        if (bioAuthUser.isNotBlank()) {
            val array = bioAuthUser.split("^^")
            if (array.isNotEmpty() && array.size == 2) {
                if (array[0] == unitId) {
                    isAuthed = true
                }
            }
        }
        val userId = if(isAuthed)  "" else unitId+"^^"+O2SDKManager.instance().distinguishedName

        O2SDKManager.instance().prefs().edit{
            putString(BioConstants.O2_bio_auth_user_id_prefs_key, userId)
        }
        if (isAuthed) {
            image_btn_account_security_biometry_enable.setImageResource(R.mipmap.icon_toggle_off_29dp)
        }else {
            image_btn_account_security_biometry_enable.setImageResource(R.mipmap.icon_toggle_on_29dp)
        }

    }


    private fun changeMobile() {
        O2DialogSupport.openConfirmDialog(this, "????????????????????????????????????,????????????????????????????????????????????????????????????", {
            val deviceId = O2SDKManager.instance().prefs().getString(O2.PRE_BIND_PHONE_TOKEN_KEY, "") ?: ""
            mPresenter.logout(deviceId)
        })
    }



}
