package net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.o2.launch

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import cn.jpush.android.api.JPushInterface
import kotlinx.android.synthetic.main.activity_launch.*
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.*
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.base.BaseMVPActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.o2.DownloadAPKFragment
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.o2.bind.BindPhoneActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.o2.login.LoginActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.o2.main.MainActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.core.component.enums.LaunchState
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.core.receiver.NetworkConnectStatusReceiver
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.extension.*
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.permission.PermissionRequester
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.widgets.dialog.O2AlertDialogBuilder
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.widgets.dialog.O2AlertIconEnum
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.widgets.dialog.O2DialogSupport
import org.jetbrains.anko.dip
import androidx.annotation.RequiresApi
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.core.service.DownloadAPKService
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.bo.O2AppUpdateBean
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.*


/**
 * Created by fancy on 2017/6/7.
 */

class LaunchActivity : BaseMVPActivity<LaunchContract.View, LaunchContract.Presenter>(), LaunchContract.View {

    private var pushToken = ""
    //?????????
    val introductionArray = intArrayOf(R.mipmap.introduction1, R.mipmap.introduction2, R.mipmap.introduction3,
            R.mipmap.introduction4, R.mipmap.introduction5)
    private val indicatorList = ArrayList<ImageView>()

    private var mStyleUpdate = false
    private var mCheckNetwork:Boolean? = null

    private val networkReceiver by lazy {
        NetworkConnectStatusReceiver { isConnected ->
            Log.d("LaunchActivity", "???????????????????????????isConnected:$isConnected")
            if (isConnected && mCheckNetwork == false){
                checkAppUpdate()
            }
        }
    }

    override var mPresenter: LaunchContract.Presenter = LaunchPresenter()

    override fun layoutResId(): Int = R.layout.activity_launch

    override fun beforeSetContentView() {
        super.beforeSetContentView()
        setTheme(R.style.XBPMTheme_NoActionBar)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)//???????????????
    }

    override fun afterSetContentView(savedInstanceState: Bundle?) {
        val deviceId = O2SDKManager.instance().prefs().getString(O2.PRE_BIND_PHONE_TOKEN_KEY, "") ?: ""//?????????????????????????????????
        if (!TextUtils.isEmpty(deviceId)) {
            Log.d("LaunchActivity", "????????????????????????$deviceId")
            pushToken = deviceId
        }
        if (TextUtils.isEmpty(pushToken)) {
            val nowToken = JPushInterface.getRegistrationID(this)
            if (!TextUtils.isEmpty(nowToken)) {
                pushToken = nowToken
            }
            Log.d("LaunchActivity", "?????????????????????deviceId???$pushToken")
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter()
        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE")
        registerReceiver(networkReceiver, filter)
        //init introduction
        if (showIntroductionView()) {
            initIntroductionUI()
        }else {
            frame_launch_introduction_content.gone()
            constraint_launch_main_content.visible()
            start()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(networkReceiver)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    fun start() {
        tv_launch_status.text = getString(R.string.launch_start) //????????????
        circleProgressBar_launch.visible()
        if (CheckRoot.isDeviceRooted()) {
            O2DialogSupport.openAlertDialog(this, "?????????Root?????????App???????????????")
        }else {
            PermissionRequester(this)
                    .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .o2Subscribe {
                        onNext { (granted, shouldShowRequestPermissionRationale, deniedPermissions) ->
                            Log.d("LaunchActivity", "granted:$granted, show:$shouldShowRequestPermissionRationale, deniedList:$deniedPermissions")
                            if (!granted) {
                                O2DialogSupport.openAlertDialog(this@LaunchActivity, "???????????????????????????????????????????????????????????? ???????????????", { permissionSetting() })
                            } else {
                                checkNetwork()
                            }
                        }
                        onError { e, _ ->
                            Log.e("LaunchActivity", "??????????????????", e)
                        }
                    }
        }
    }

    /**
     * ??????????????????
     */
    private fun checkNetwork(){
        val cManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cManager.activeNetworkInfo
        if (activeNetwork == null || !activeNetwork.isConnected) {
            mCheckNetwork = false
            tv_launch_status.text = getString(R.string.launch_network_is_not_connected) //?????? ????????????
            XToast.toastShort(this, getString(R.string.launch_network_is_not_connected))
        }else{
            mCheckNetwork = true
            // ????????????????????? launch()
            checkAppUpdate()
        }
    }


    ////////////////////////////?????? start///////////////////////////////////

    /**
     * ??????????????????????????????
     */

    private var downloadFragment: DownloadAPKFragment? = null
    private var versionName = ""
    private var downloadUrl = ""

    private fun checkAppUpdate() {
        checkAppUpdate(callbackContinue = { result ->
            if (result) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {// 8.0???????????????????????????????????????
                    startInstallPermissionSettingActivity()
                }else { // ??????????????????
                    if (downloadFragment == null) {
                        downloadFragment = DownloadAPKFragment()
                    }
                    downloadFragment?.isCancelable = false
                    if (downloadFragment?.isAdded == true) {
                    }else {
                        downloadFragment?.show(supportFragmentManager, DownloadAPKFragment.DOWNLOAD_FRAGMENT_TAG)
                        downloadServiceStart()
                    }

                }
            }else {
                launch()
            }
        })
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == 10086) { //  ??????????????????
            if (downloadFragment == null) {
                downloadFragment = DownloadAPKFragment()
            }
            downloadFragment?.isCancelable = false
            if (downloadFragment?.isAdded == true) {
            }else {
                downloadFragment?.show(supportFragmentManager, DownloadAPKFragment.DOWNLOAD_FRAGMENT_TAG)
                downloadServiceStart()
            }

        }
    }
    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun startInstallPermissionSettingActivity() {
        //???????????????8.0???API
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        startActivityForResult(intent, 10086)
    }
    private fun checkAppUpdate(callbackContinue:((flag: Boolean)->Unit)? = null) {
        O2AppUpdateManager.instance().checkUpdate(this, object : O2AppUpdateCallback {
            override fun onUpdate(version: O2AppUpdateBean) {
                XLog.debug("onUpdateAvailable $version")
                versionName = version.versionName
                downloadUrl = version.downloadUrl
                XLog.info("versionName:$versionName, downloadUrl:$downloadUrl")
                O2DialogSupport.openConfirmDialog(this@LaunchActivity,"?????? $versionName ?????????"+version.content, listener = { _ ->
                    XLog.info("notification is true..........")
                    callbackContinue?.invoke(true)
                }, icon = O2AlertIconEnum.UPDATE, negativeListener = {_->
                    callbackContinue?.invoke(false)
                })
            }

            override fun onNoneUpdate(error: String) {
                XLog.info(error)
                callbackContinue?.invoke(false)
            }

        })
    }
    private fun downloadServiceStart() {
        val intent = Intent(this, DownloadAPKService::class.java)
        intent.action = packageName + DownloadAPKService.DOWNLOAD_SERVICE_ACTION
        intent.putExtra(DownloadAPKService.VERSIN_NAME_EXTRA_NAME, versionName)
        intent.putExtra(DownloadAPKService.DOWNLOAD_URL_EXTRA_NAME, downloadUrl)
        startService(intent)
    }

    /////////////////////////////////////?????? end//////////////////////////////////////


    private fun launch() {
        XLog.info("????????????????????????????????????????????????????????????????????????????????????")
        if (BuildConfig.InnerServer) {
            val json = resources.assets.open("server.json")
            if (json!= null) {
                val len = json.available()
                val buffer = ByteArray(len)
                json.read(buffer)
                O2SDKManager.instance().launchInner(String(buffer, Charsets.UTF_8), launchState)
            }else {
                XLog.error("???????????????server.json")
//                XToast.toastShort(this, "?????????????????????")
                O2AlertDialogBuilder(this)
                        .icon(O2AlertIconEnum.ALERT)
                        .content("??????????????????????????????App???????????????")
                        .positive("??????")
                        .onPositiveListener{ _ ->
                            finish()
                        }
                        .show()
            }
        }else {
            if (TextUtils.isEmpty(pushToken)) {
                // ??????3??? ???????????????token
                Handler().postDelayed({
                    XLog.debug("delay 3 second check bind device")
                    val nowToken = JPushInterface.getRegistrationID(this)
                    XLog.debug("device : $nowToken")
                    O2SDKManager.instance().launch(nowToken, launchState)
                }, 3000)
            } else {
                O2SDKManager.instance().launch(pushToken, launchState)
            }

        }
    }

    /**
     *  ????????????
     */
    private val launchState : (state: LaunchState)->Unit = { state->
        runOnUiThread {
            when (state) {
                LaunchState.ConnectO2Collect -> {
                    tv_launch_status.text = getString(R.string.launch_check_bind) //????????????
                }
                LaunchState.ConnectO2Server -> {
                    tv_launch_status.text = getString(R.string.launch_load_center) //?????????????????????
                }
                LaunchState.CheckMobileConfig -> {
                    tv_launch_status.text = getString(R.string.launch_check_style) //????????????
                }
                LaunchState.DownloadMobileConfig -> {
                    mStyleUpdate = true
                    tv_launch_status.text = getString(R.string.launch_download_style) //????????????
                }
                LaunchState.AutoLogin -> {
                    if (mStyleUpdate) {
                        mPresenter.downloadConfig()
                    }
                    tv_launch_status.text = getString(R.string.launch_auto_login) //????????????
                    val path = O2CustomStyle.launchLogoImagePath(this)
                    if (!TextUtils.isEmpty(path)) {
                        BitmapUtil.setImageFromFile(path!!, image_launch_logo)
                    }
                }
                LaunchState.NoBindError -> {
                    gotoBindLogin()
                }
                LaunchState.NoLoginError -> {
                    gotoLogin()
                }
                LaunchState.UnknownError -> {
                    if (!isFinishing) {
                        if (BuildConfig.InnerServer) {
                            O2AlertDialogBuilder(this)
                                    .icon(O2AlertIconEnum.ALERT)
                                    .content("???????????????")
                                    .positive("??????")
                                    .onPositiveListener { _ ->
                                        finish()
                                    }
                                    .show()
                        } else {
                            O2AlertDialogBuilder(this)
                                    .title(R.string.confirm)
                                    .icon(O2AlertIconEnum.ALERT)
                                    .content("???????????????")
                                    .positive("????????????")
                                    .negative("??????")
                                    .onPositiveListener { _ ->
                                        gotoBindLogin()
                                    }
                                    .onNegativeListener { _ ->
                                        finish()
                                    }
                                    .show()
                        }
                    }
                }
                LaunchState.Success -> {
                    gotoMain()
                }
            }
        }
    }



    private fun gotoBindLogin() {
        circleProgressBar_launch.gone()
        goThenKill<BindPhoneActivity>()
    }

    private fun gotoLogin() {
        circleProgressBar_launch.gone()
        goThenKill<LoginActivity>()
    }

    private fun gotoMain() {
        circleProgressBar_launch.gone()
        if (mStyleUpdate) {
            goAndClearBefore<MainActivity>()
        } else {
            goThenKill<MainActivity>()
        }
    }

    /**
     * ??????????????????
     */
    private fun permissionSetting() {
        val pkName = packageName
        val packageURI = Uri.parse("package:$pkName")
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageURI)
        startActivity(intent)
        finish()
    }


    private fun showIntroductionView(): Boolean =
            O2SDKManager.instance().prefs().getBoolean(O2.PRE_LAUNCH_INTRODUCTION_KEY, true)


    private fun initIntroductionUI() {
        frame_launch_introduction_content.visible()
        constraint_launch_main_content.gone()
        //??????indicator
        introductionArray.map {
            val indicator = ImageView(this@LaunchActivity)
            indicator.setImageResource(R.mipmap.ic_launch_introduction_indicator_dark)
            val param = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            param.setMargins(dip(10), 0, dip(10), 0)
            indicator.layoutParams = param
            linear_launch_introduction_bottom_indicator.addView(indicator)
            indicatorList.add(indicator)
        }
        view_pager_launch_introduction_page.adapter = object : PagerAdapter() {

            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                var page = container?.findViewWithTag<ImageView>(introductionPageTag(position))
                if (page == null) {
                    page = ImageView(this@LaunchActivity)
                    page.setImageResource(introductionArray[position])
                    page.scaleType = ImageView.ScaleType.FIT_XY
                    page.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    page.tag = introductionPageTag(position)
                    (container as ViewPager).addView(page)
                }
                return page
            }

            override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
                `object` as ImageView
                container.removeView(`object`)
            }

            override fun isViewFromObject(view: View, `object`: Any): Boolean = view == `object`

            override fun getCount(): Int = introductionArray.size
        }
        view_pager_launch_introduction_page.addOnPageChangeListener {
            onPageSelected { position ->
                if (position == indicatorList.size - 1) {
                    btn_launch_introduction_bottom_enter.visible()
                    linear_launch_introduction_bottom_indicator.gone()
                } else {
                    btn_launch_introduction_bottom_enter.gone()
                    linear_launch_introduction_bottom_indicator.visible()
                    indicatorList.map { it.setImageResource(R.mipmap.ic_launch_introduction_indicator_dark) }
                    indicatorList[position].setImageResource(R.mipmap.ic_launch_introduction_indicator_light)
                }

            }
        }
        view_pager_launch_introduction_page.currentItem = 0
        indicatorList[0].setImageResource(R.mipmap.ic_launch_introduction_indicator_light)

        btn_launch_introduction_bottom_enter.setOnClickListener {
            O2SDKManager.instance().prefs().edit {
                putBoolean(O2.PRE_LAUNCH_INTRODUCTION_KEY, false)
            }
            frame_launch_introduction_content.gone()
            constraint_launch_main_content.visible()
            start()
        }
    }

    private fun introductionPageTag(position: Int): String = "page_$position"

}