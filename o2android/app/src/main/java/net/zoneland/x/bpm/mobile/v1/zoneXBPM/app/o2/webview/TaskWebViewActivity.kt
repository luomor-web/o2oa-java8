package net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.o2.webview


import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import android.text.TextUtils
import android.util.TypedValue.COMPLEX_UNIT_SP
import android.view.Gravity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.activity_work_web_view.*
import net.muliba.fancyfilepickerlibrary.FilePicker
import net.muliba.fancyfilepickerlibrary.PicturePicker
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.O2App
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.O2SDKManager
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.R
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.base.BaseMVPActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.tbs.FileReaderActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.core.component.api.APIAddressHelper
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.bo.WorkNewActionItem
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.bo.api.WorkControl
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.bo.api.o2.ProcessDraftWorkData
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.bo.api.o2.ReadData
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.bo.api.o2.WorkOpinionData
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.vo.O2UploadImageData
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.*
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.extension.go
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.extension.gone
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.extension.o2Subscribe
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.extension.visible
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.permission.PermissionRequester
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.widgets.BottomSheetMenu
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.widgets.WebChromeClientWithProgressAndValueCallback
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.widgets.dialog.O2DialogSupport
import org.jetbrains.anko.dip
import org.jetbrains.anko.doAsync
import rx.Observable
import rx.Scheduler
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


class TaskWebViewActivity : BaseMVPActivity<TaskWebViewContract.View, TaskWebViewContract.Presenter>(), TaskWebViewContract.View {
    override var mPresenter: TaskWebViewContract.Presenter = TaskWebViewPresenter()


    override fun layoutResId(): Int = R.layout.activity_work_web_view

    companion object {
        val WORK_WEB_VIEW_TITLE = "xbpm.work.web.view.title"
        val WORK_WEB_VIEW_WORK = "xbpm.work.web.view.work"
        val WORK_WEB_VIEW_WORK_COMPLETED = "xbpm.work.web.view.work.completed"
        val WORK_WEB_VIEW_DRAFT = "xbpm.work.web.view.work.draft"

        fun start(work: String?, workCompleted: String?, title: String?):  Bundle {
            val bundle = Bundle()
            bundle.putString(WORK_WEB_VIEW_TITLE, title)
            bundle.putString(WORK_WEB_VIEW_WORK, work)
            bundle.putString(WORK_WEB_VIEW_WORK_COMPLETED, workCompleted)
            return bundle
        }

        fun startDraft(draft: ProcessDraftWorkData?):  Bundle {
            val bundle = Bundle()
            bundle.putSerializable(WORK_WEB_VIEW_DRAFT, draft)
            return bundle
        }
    }

    private  val WORK_WEB_VIEW_UPLOAD_REQUEST_CODE = 1001
    private  val WORK_WEB_VIEW_REPLACE_REQUEST_CODE = 1002
    private  val TAKE_FROM_PICTURES_CODE = 1003
    private  val TAKE_FROM_CAMERA_CODE = 1004

    private var title = ""
    private  var workId = ""
    private  var workCompletedId = ""
    private  var isWorkCompleted = false
    private  var url = ""
    private var draft: ProcessDraftWorkData? = null

    private var control: WorkControl? = null
    private var read: ReadData? = null
    private var site = ""
    private var attachmentId = ""
    private var formData: String? = ""//??????json??????
    private var formOpinion: String? = ""// ???????????????????????????
    private val routeNameList = ArrayList<String>()

    private val downloadDocument: DownloadDocument by lazy { DownloadDocument(this) }
    private val cameraImageUri: Uri by lazy { FileUtil.getUriFromFile(this, File(FileExtensionHelper.getCameraCacheFilePath())) }
    private val webChromeClient: WebChromeClientWithProgressAndValueCallback by lazy { WebChromeClientWithProgressAndValueCallback.with(this) }
    var imageUploadData: O2UploadImageData? = null
    private val jsNotification: JSInterfaceO2mNotification by lazy { JSInterfaceO2mNotification.with(this) }
    private val jsUtil: JSInterfaceO2mUtil by lazy { JSInterfaceO2mUtil.with(this) }
    private val jsBiz: JSInterfaceO2mBiz by lazy { JSInterfaceO2mBiz.with(this) }




    override fun afterSetContentView(savedInstanceState: Bundle?) {
        title = intent.extras?.getString(WORK_WEB_VIEW_TITLE) ?: ""
        workId = intent.extras?.getString(WORK_WEB_VIEW_WORK) ?: ""
        workCompletedId = intent.extras?.getString(WORK_WEB_VIEW_WORK_COMPLETED) ?: ""
        draft = if (intent.extras?.getSerializable(WORK_WEB_VIEW_DRAFT) != null){
            intent.extras?.getSerializable(WORK_WEB_VIEW_DRAFT) as ProcessDraftWorkData
        } else {
            null
        }

        //??????????????????
        if (draft != null) {
            if (!TextUtils.isEmpty(draft!!.id)){
                url = APIAddressHelper.instance().getProcessDraftWithIdUrl()
                url = String.format(url, draft!!.id)
            }else {
                url = APIAddressHelper.instance().getProcessDraftUrl()
                val json = O2SDKManager.instance().gson.toJson(draft)
                XLog.debug("????????????:$json")
                val enJson = URLEncoder.encode(json, "utf-8")
                XLog.debug("???????????? encode:$enJson")
                url = String.format(url, enJson)
            }
        }else {
            isWorkCompleted = !TextUtils.isEmpty(workCompletedId)
            if (isWorkCompleted) {
                url = APIAddressHelper.instance().getWorkCompletedUrl()
                url = String.format(url, workCompletedId)
            } else {
                url = APIAddressHelper.instance().getWorkUrlPre()
                url = String.format(url, workId)
            }
        }
        url += "&time=" + System.currentTimeMillis()

        XLog.debug("title:$title ,  url:$url")
        setupToolBar(title, true)
        toolbar?.setNavigationOnClickListener {
            XLog.debug("????????????????????????????????????????????????????????????")
            processCheckNew()
        }

        web_view.addJavascriptInterface(this, "o2android")
        jsNotification.setupWebView(web_view)
        jsUtil.setupWebView(web_view)
        jsBiz.setupWebView(web_view)
        web_view.addJavascriptInterface(jsNotification, JSInterfaceO2mNotification.JSInterfaceName)
        web_view.addJavascriptInterface(jsUtil, JSInterfaceO2mUtil.JSInterfaceName)
        web_view.addJavascriptInterface(jsBiz, JSInterfaceO2mBiz.JSInterfaceName)
        web_view.webChromeClient = webChromeClient
        web_view.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                XLog.error("ssl error, $error")
                handler?.proceed()
            }
            override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
                XLog.debug("shouldOverrideUrlLoading:$url")
                if (ZoneUtil.checkUrlIsInner(url)) {
                    view?.loadUrl(url)
                } else {
                    AndroidUtils.runDefaultBrowser(this@TaskWebViewActivity, url)
                }
                return true
            }

        }


        web_view.webViewSetCookie(this, url)
        web_view.loadUrl(url)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            // ????????? js ?????????????????????
            if (webChromeClient.onActivityResult(requestCode, resultCode, data)) {
                return
            }
            when (requestCode) {
                WORK_WEB_VIEW_UPLOAD_REQUEST_CODE -> {
                    val result = data?.getStringExtra(FilePicker.FANCY_FILE_PICKER_SINGLE_RESULT_KEY)
                    if (!TextUtils.isEmpty(result)) {
                        XLog.debug("uri path:$result")
                        showLoadingDialog()
                        mPresenter.uploadAttachment(result!!, site, workId)
                    } else {
                        XLog.error("FilePicker ??????????????????")
                    }
                }
                WORK_WEB_VIEW_REPLACE_REQUEST_CODE -> {
                    val result = data?.getStringExtra(FilePicker.FANCY_FILE_PICKER_SINGLE_RESULT_KEY)
                    if (!TextUtils.isEmpty(result)) {
                        XLog.debug("uri path:$result")
                        showLoadingDialog()
                        mPresenter.replaceAttachment(result!!, site, attachmentId, workId)
                    } else {
                        XLog.error("FilePicker ??????????????????")
                    }
                }
                TAKE_FROM_PICTURES_CODE -> {
                    //????????????
                    data?.let {
                        val result = it.extras?.getString(PicturePicker.FANCY_PICTURE_PICKER_SINGLE_RESULT_KEY, "")
                        if (!TextUtils.isEmpty(result)) {
                            XLog.debug("?????? path:$result")
                            uploadImage2FileStorageStart(result!!)
                        }
                    }
                }
                TAKE_FROM_CAMERA_CODE -> {
                    //??????
                    XLog.debug("??????//// ")
                    uploadImage2FileStorageStart(FileExtensionHelper.getCameraCacheFilePath())
                }
            }
        }
    }

    //MARK???- ????????????

    //region ??????????????????

    /**
     * ????????????
     */
    fun formDeleteBtnClick(view: View?) {
        O2DialogSupport.openConfirmDialog(this@TaskWebViewActivity, getString(R.string.delete_work_confirm_message), listener =  {
            showLoadingDialog()
            mPresenter.delete(workId)
        })
    }

    /**
     * ????????????
     */
    fun formSaveBtnClick(view: View?) {
        XLog.debug("click save button")
        web_view.clearFocus()
        evaluateJavascriptGetFormDataAndSave()
    }

    /**
     * ????????????
     */
    fun formGoNextBtnClick(view: View?) {
        XLog.debug("click submit button")
        web_view.clearFocus()
        formData{
            getFormOpinion{
                submitData()
            }
        }
    }

    /**
     * ???????????????
     */
    fun formSetReadBtnClick(view: View?) {
        O2DialogSupport.openConfirmDialog(this@TaskWebViewActivity, getString(R.string.read_complete_confirm_message), listener =  {
            showLoadingDialog()
            mPresenter.setReadComplete(read)
        })
    }

    /**
     * ????????????
     */
    fun formRetractBtnClick(view: View?) {
        O2DialogSupport.openConfirmDialog(this@TaskWebViewActivity, getString(R.string.retract_confirm_message), listener = {
            showLoadingDialog()
            mPresenter.retractWork(workId)
        })
    }

    //endregion

    // MARK: - finish submit callback webview javascript
    /**
     * @param site ???????????????????????? ??????site???
     */
    fun finishSubmit(site: String?) {
        if (!TextUtils.isEmpty(site)) {
            XLog.info("finish submit ...$site")
        }
        finish()
    }


    // MARK: - javascriptInterface

    //region javascriptInterface
    @JavascriptInterface
    fun closeWork(result: String) {
        XLog.debug("???????????? closeWork ???$result")
        finish()
    }

    /**
     * ???????????????????????????
     */
    @JavascriptInterface
    fun appFormLoaded(result: String) {// ??????control ????????????????????????
        XLog.debug("???????????????????????????$result")// 20190520 result??????????????????????????? ?????????result???true?????????????????????????????????????????????????????????????????????????????????
        //2019-12-09 ??????workwithaction???html ???????????????????????????????????????
//        runOnUiThread {
//            if (TextUtils.isEmpty(title)) {
//                web_view.evaluateJavascript("layout.app.appForm.businessData.work.title") { value ->
//                    XLog.debug("title: $title")
//                    try {
//                        title = O2SDKManager.instance().gson.fromJson(value, String::class.java)
//                        updateToolbarTitle(title)
//                    } catch (e: Exception) {
//                    }
//                }
//            }
//
//            if (result == "true") { // ??????????????????
//                // ??????control ??????????????????
//                web_view.evaluateJavascript("layout.app.appForm.businessData.control") { value ->
//                    XLog.debug("control: $value")
//                    try {
//                        control = O2SDKManager.instance().gson.fromJson(value, WorkControl::class.java)
//                    } catch (e: Exception) {
//                    }
//                    initOptionBar()
//                }
//            }else {// 2019-05-21 ????????????????????????
//                // ??????result ??????????????????
//                if (!TextUtils.isEmpty(result)) {
//                    try {
//                        val type = object : TypeToken<List<WorkNewActionItem>>() {}.type
//                        val list: List<WorkNewActionItem> = O2SDKManager.instance().gson.fromJson(result, type)
//                        initOptionBarNew(list)
//                    }catch (e: Exception){
//                        XLog.error("????????????????????????????????????", e)
//                    }
//                }else {
//                    XLog.error("????????????????????????")
//                }
//
//            }
//
//            web_view.evaluateJavascript("layout.app.appForm.businessData.read") { value ->
//                XLog.debug("read: $value")
//                try {
//                    read = O2SDKManager.instance().gson.fromJson(value, ReadData::class.java)
//                } catch (e: Exception) {
//                }
//            }
//        }
    }



    /**
     * ????????????
     *
     * @param site
     */
    @JavascriptInterface
    fun uploadAttachment(site: String) {
        XLog.debug("upload site:$site")
        if (TextUtils.isEmpty(site)) {
            XLog.error("????????????site")
            return
        }
        this.site = site
        openFancyFilePicker(WORK_WEB_VIEW_UPLOAD_REQUEST_CODE)
    }

    /**
     * ????????????
     *
     * @param attachmentId
     * @param site
     */
    @JavascriptInterface
    fun replaceAttachment(attachmentId: String, site: String) {
        XLog.debug("replace site:$site, attachmentId:$attachmentId")
        if (TextUtils.isEmpty(attachmentId) || TextUtils.isEmpty(site)) {
            XLog.error("????????????attachmentId ??? site")
            return
        }
        this.site = site
        this.attachmentId = attachmentId
        openFancyFilePicker(WORK_WEB_VIEW_REPLACE_REQUEST_CODE)
    }

    /**
     * ????????????
     *
     * @param attachmentId
     */
    @JavascriptInterface
    fun downloadAttachment(attachmentId: String) {
        XLog.debug("download attachmentId:$attachmentId")
        if (TextUtils.isEmpty(attachmentId)) {
            XLog.error("?????????????????????id???????????????")
            return
        }
        runOnUiThread {
            showLoadingDialog()
        }
        if (isWorkCompleted) {
            mPresenter.downloadWorkCompletedAttachment(attachmentId, workCompletedId)
        }else {
            mPresenter.downloadAttachment(attachmentId, workId)
        }
    }

    /**
     * ???????????? ???????????? office pdf ???
     */
    @JavascriptInterface
    fun openDocument(url: String) {
        XLog.debug("??????????????????????????????????????????$url")
        runOnUiThread {
            showLoadingDialog()
        }
        downloadDocument.downloadDocumentAndOpenIt(url) {
            hideLoadingDialog()
        }
    }

    /**
     * ????????? js?????????
     */
    @JavascriptInterface
    fun openO2Alert(message: String?) {
        if (message != null) {
            XLog.debug("???????????????message:$message")
            runOnUiThread {
                O2DialogSupport.openAlertDialog(this, message)
            }
        }
    }

    /**
     * ????????????
     */
    @JavascriptInterface
    fun uploadImage2FileStorage(json: String?) {
        imageUploadData = null
        XLog.debug("??????????????????????????? $json")
        runOnUiThread {
            if (json != null) {
                imageUploadData = O2SDKManager.instance().gson.fromJson(json, O2UploadImageData::class.java)
                showPictureChooseMenu()

            }else {
                XToast.toastShort(this, "??????????????????")
            }
        }
    }
    //endregion

    //MARK: - view implements

    //region view implements

    override fun finishLoading() {
        XLog.debug("finishLoading.........")
        hideLoadingDialog()
    }

    override fun saveSuccess() {
        XLog.debug("savesucess.........")
        evaluateJavascriptAfterSave {
            hideLoadingDialog()
            XToast.toastShort(this, "???????????????")
        }
    }
    override fun submitSuccess() {
        hideLoadingDialog()
        finish()
    }

    override fun setReadCompletedSuccess() {
        hideLoadingDialog()
        finish()
    }

    override fun retractSuccess() {
        hideLoadingDialog()
        XToast.toastShort(this, "???????????????")
        finish()
    }

    override fun retractFail() {
        hideLoadingDialog()
        XToast.toastShort(this, "???????????????")
    }

    override fun deleteSuccess() {
        hideLoadingDialog()
        XToast.toastShort(this, "???????????????")
        finish()
    }

    override fun deleteFail() {
        hideLoadingDialog()
        XToast.toastShort(this, "???????????????")
    }

    override fun uploadAttachmentSuccess(attachmentId: String, site: String) {
        XLog.debug("uploadAttachmentResponse attachmentId:$attachmentId, site:$site")
        hideLoadingDialog()
        web_view.evaluateJavascript("layout.app.appForm.uploadedAttachment(\"$site\", \"$attachmentId\")"){
            value -> XLog.debug("uploadedAttachment??? onReceiveValue value=$value")
        }
    }

    override fun replaceAttachmentSuccess(attachmentId: String, site: String) {
        XLog.debug("replaceAttachmentResponse attachmentId:$attachmentId, site:$site")
        hideLoadingDialog()
        web_view.evaluateJavascript("layout.app.appForm.replacedAttachment(\"$site\", \"$attachmentId\")"){
            value -> XLog.debug("replacedAttachment??? onReceiveValue value=$value")
        }
    }

    override fun downloadAttachmentSuccess(file: File) {
        hideLoadingDialog()
//        if (file.exists()) AndroidUtils.openFileWithDefaultApp(this, file)
        if (file.exists()){
            if (FileExtensionHelper.isImageFromFileExtension(file.extension)) {
                go<LocalImageViewActivity>(LocalImageViewActivity.startBundle(file.absolutePath))
            }else {
                go<FileReaderActivity>(FileReaderActivity.startBundle(file.absolutePath))
//                QbSdk.openFileReader(this, file.absolutePath, HashMap<String, String>()) { p0 -> XLog.info("?????????????????????????????????$p0") }
            }
        }
    }

    override fun invalidateArgs() {
        XToast.toastShort(this, "?????????????????????")
    }

    override fun downloadFail(message: String) {
        finishLoading()
        XToast.toastShort(this, message)
    }

    override fun upload2FileStorageFail(message: String) {
        hideLoadingDialog()
        XToast.toastShort(this, message)
    }

    override fun upload2FileStorageSuccess(id: String) {
        hideLoadingDialog()
        if (imageUploadData != null) {
            imageUploadData!!.fileId = id
            val callback = imageUploadData!!.callback
            val json = O2SDKManager.instance().gson.toJson(imageUploadData)
            val js = "$callback('$json')"
            XLog.debug("??????js:$js")
            web_view.evaluateJavascript(js){
                value -> XLog.debug("replacedAttachment??? onReceiveValue value=$value")
            }
        }else {
            XLog.error("???????????????????????????????????????????????????")
        }
    }
    //endregion


    //region  private function

    /**
     * ????????????
     * ????????????????????????????????? ?????????????????????
     */
    private fun processCheckNew() {
        web_view.evaluateJavascript("layout.app.appForm.finishOnMobile()"){
            value -> XLog.debug("finishOnMobile /??????????????????????????????????????????$value")
            try {
                finish()
            }catch (e: Exception){
                XLog.error("", e)
            }
        }
    }

    /**
     * ??????????????????
     */
    private fun initOptionBar() {
        XLog.debug("initOptionBar......??????????????????")
        if (control != null) {
            var count = 0
            if (control?.allowDelete == true) {
                count ++
                tv_work_form_delete_btn.visible()
            }
            if (control?.allowSave == true) {
                count ++
                tv_work_form_save_btn.visible()
            }
            if (control?.allowProcessing == true) {
                    count ++
                    tv_work_form_go_next_btn.visible()
                }
            if (control?.allowReadProcessing == true) {
                    count ++
                    tv_work_form_set_read_btn.visible()
                }
            if (control?.allowRetract == true) {
                    count ++
                    tv_work_form_retract_btn.visible()
            }
            if (count > 0 ) {
                bottom_operate_button_layout.visible()
                fl_bottom_operation_bar.visible()
            }
        }else {
            XLog.error("control????????????????????????")
        }
    }


    /**
     * 20190521
     * ?????????????????? ??????
     */
    private fun initOptionBarNew(list: List<WorkNewActionItem>) {
        if(list.isNotEmpty()) {
            when(list.count()) {
                1 -> {
                    val menuItem = list[0]
                    tv_work_form_bottom_first_action.text = menuItem.text
                    tv_work_form_bottom_first_action.visible()
                    tv_work_form_bottom_first_action.setOnClickListener {
                        bottomButtonAction(menuItem)
                    }
                }
                2 -> {
                    val menuItem = list[0]
                    tv_work_form_bottom_first_action.text = menuItem.text
                    tv_work_form_bottom_first_action.visible()
                    tv_work_form_bottom_first_action.setOnClickListener {
                        bottomButtonAction(menuItem)
                    }
                    val menuItem2 = list[1]
                    tv_work_form_bottom_second_action.text = menuItem2.text
                    tv_work_form_bottom_second_action.visible()
                    tv_work_form_bottom_second_action.setOnClickListener {
                        bottomButtonAction(menuItem2)
                    }
                }
                else -> {
                    val menuItem = list[0]
                    tv_work_form_bottom_first_action.text = menuItem.text
                    tv_work_form_bottom_first_action.visible()
                    tv_work_form_bottom_first_action.setOnClickListener {
                        bottomButtonAction(menuItem)
                    }
                    val menuItem2 = list[1]
                    tv_work_form_bottom_second_action.text = menuItem2.text
                    tv_work_form_bottom_second_action.visible()
                    tv_work_form_bottom_second_action.setOnClickListener {
                        bottomButtonAction(menuItem2)
                    }
                    img_work_form_bottom_more_action.visible()
                    img_work_form_bottom_more_action.setOnClickListener {
                        if (rl_bottom_operation_bar_mask.visibility == View.VISIBLE) {
                            rl_bottom_operation_bar_mask.gone()
                        }else {
                            rl_bottom_operation_bar_mask.visible()
                        }
                    }
                    rl_bottom_operation_bar_mask.setOnClickListener {
                        XLog.debug("??????????????????????????????")
                        rl_bottom_operation_bar_mask.gone()
                    }
                    //??????????????????
                    ll_bottom_operation_bar_new_more.removeAllViews()
                    for ((index, item) in list.withIndex()) {
                       if (index > 1) {
                           val button = newBottomMoreButton(item)
                           ll_bottom_operation_bar_new_more.addView(button)
                           button.setOnClickListener {
                               bottomButtonAction(item)
                           }
                       }
                    }
                }

            }
            fl_bottom_operation_bar.visible()
            ll_bottom_operation_bar_new.visible()
        }
    }

    /**
     * ??????????????????????????????
     */
    private fun bottomButtonAction(menuItem: WorkNewActionItem) {
        XLog.debug("???????????????${menuItem.text}")
        XLog.debug("?????????${menuItem.action} , control:${menuItem.control}")

        if (!TextUtils.isEmpty(menuItem.actionScript)) {
            val jsExc = "layout.app.appForm._runCustomAction(${menuItem.actionScript})"
            XLog.debug(jsExc)
            web_view.evaluateJavascript(jsExc) { value ->
                XLog.debug("onReceiveValue value=$value")
            }
        }else {
            when(menuItem.control) {
                "allowDelete" -> {
                    formDeleteBtnClick(null)
                }
                "allowSave" -> {
                    formSaveBtnClick(null)
                }
                "allowProcessing" -> {
                    formGoNextBtnClick(null)
                }
                "allowReadProcessing" ->{
                    formSetReadBtnClick(null)
                }
                "allowRetract" -> {
                    formRetractBtnClick(null)
                }
                else -> {
                    val jsExc ="layout.app.appForm[\"${menuItem.action}\"]()"
                    XLog.debug(jsExc)
                    web_view.evaluateJavascript(jsExc) { value ->
                        XLog.debug("onReceiveValue value=$value")
                    }
                }
            }
        }
        rl_bottom_operation_bar_mask.gone()
    }

    /**
     * ??????????????????
     */
    private fun newBottomMoreButton(menuItem: WorkNewActionItem): TextView {
        val button = TextView(this)
        val layoutparam = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dip(42))
        layoutparam.bottomMargin = dip(5)
        button.layoutParams = layoutparam
        button.gravity = Gravity.CENTER
        button.text = menuItem.text
        button.setTextColor(ContextCompat.getColor(this, R.color.z_color_primary))
        button.setBackgroundColor(Color.WHITE)
        button.setTextSize(COMPLEX_UNIT_SP, 16f)
        return button
    }

    /**
     * ????????????
     */
    private fun submitData() {
        web_view.evaluateJavascript("layout.app.appForm.formValidation(\"\", \"\")") { value ->
            XLog.debug("formValidation???value:$value")
            if (value == "true") {
                web_view.evaluateJavascript("layout.app.appForm.businessData.task") { task ->
                    XLog.debug("submitData, onReceiveValue value=$task")
                    try {
                        XLog.debug("submitData???TaskData:$task")
                        if (TextUtils.isEmpty(task)) {
                            XToast.toastShort(this@TaskWebViewActivity, "???????????????????????????")
                        }else {
                            openTaskWorkSubmitDialog(task)
                        }
                    } catch (e: Exception) {
                        XLog.error("", e)
                        XToast.toastShort(this@TaskWebViewActivity, "?????????????????????")
                    }
                }
            } else {
                XToast.toastShort(this@TaskWebViewActivity, "????????????????????????????????????")
            }
        }
    }

    /**
     * ????????????
     * ????????????????????????????????????????????????
     */
    fun validateFormForSubmitDialog(route: String, opinion: String, callback:(Boolean)->Unit) {
        web_view.evaluateJavascript("layout.app.appForm.formValidation(\"$route\", \"$opinion\")") { value ->
            if (value == "true") {
                callback(true)
            }else {
                callback(false)
            }
        }
    }

    private fun openTaskWorkSubmitDialog(taskData: String) {
        TaskWorkSubmitDialogFragment.startWorkDialog(workId, taskData, formData, formOpinion)
                .show(supportFragmentManager, TaskWorkSubmitDialogFragment.TAG)
    }


    private fun formData(callback: () -> Unit) {
        web_view.evaluateJavascript("layout.app.appForm.getData()") { value ->
            XLog.debug("evaluateJavascriptGetFormData??? onReceiveValue form value=$value")
            formData = value
            callback()
        }
    }

    private fun getFormOpinion(callback: () -> Unit) {
        web_view.evaluateJavascript("layout.app.appForm.getOpinion()") { value ->
            XLog.debug("evaluateJavascript get from Opinion??? onReceiveValue form value=$value")
            if (!TextUtils.isEmpty(value)) {
                formOpinion = if (value == "\"\"") {
                    ""
                }else {
                    var result = ""
                    try {
                        val woData = O2SDKManager.instance().gson.fromJson<WorkOpinionData>(value, WorkOpinionData::class.java)
                        result = woData.opinion ?: ""
                    } catch (e: Exception) {
                    }
                    result
                }

            }
            callback()
        }
    }


    private fun evaluateJavascriptGetFormDataAndSave() {
        showLoadingDialog()
        web_view.evaluateJavascript("(layout.app.appForm.fireEvent(\"beforeSave\");return layout.app.appForm.getData();)") { value ->
            XLog.debug("evaluateJavascriptGetFormDataAndSave??? onReceiveValue save value=$value")
            formData = value
            XLog.debug("????????????????????????")
            runOnUiThread {
                XLog.debug("runOnUiThread  ....................")
                if (formData == null || "" == formData) {
                    XLog.debug("formData is null")
                    hideLoadingDialog()
                    XToast.toastShort(this@TaskWebViewActivity, "??????????????????????????????")
                }else {
                    evaluateJavascriptBeforeSave {
                        mPresenter.save(workId, formData!!)
                    }
                }
            }
        }
    }



    /**
     * ??????beforeSave
     */
    fun evaluateJavascriptBeforeSave(callback: () -> Unit) {
        web_view.evaluateJavascript("layout.app.appForm.fireEvent(\"beforeSave\")") { value ->
            XLog.info("??????beforeSave ??? result: $value")
            callback()
        }
    }
    /**
     * ?????? afterSave
     */
    fun evaluateJavascriptAfterSave(callback: () -> Unit) {
        web_view.evaluateJavascript("layout.app.appForm.fireEvent(\"afterSave\")") { value ->
            XLog.info("??????afterSave ??? result: $value")
            callback()
        }
    }
    /**
     * ?????? beforeProcess
     */
    fun evaluateJavascriptBeforeProcess(callback: () -> Unit) {
        web_view.evaluateJavascript("layout.app.appForm.fireEvent(\"beforeProcess\")") { value ->
            XLog.info("?????? beforeProcess ??? result: $value")
            callback()
        }
    }
    /**
     * ?????? afterProcess
     */
    fun evaluateJavascriptAfterProcess(callback: () -> Unit) {
        web_view.evaluateJavascript("layout.app.appForm.fireEvent(\"afterProcess\")") { value ->
            XLog.info("?????? afterProcess ??? result: $value")
            callback()
        }
    }



    private fun showPictureChooseMenu() {
        BottomSheetMenu(this)
                .setTitle("????????????")
                .setItem("???????????????", resources.getColor(R.color.z_color_text_primary)) {
                    takeFromPictures()
                }
                .setItem("??????", resources.getColor(R.color.z_color_text_primary)) {
                    takeFromCamera()
                }
                .setCancelButton("??????", resources.getColor(R.color.z_color_text_hint)) {
                    XLog.debug("?????????????????????")
                }
                .show()
    }

    private fun takeFromPictures() {
        PicturePicker()
                .withActivity(this)
                .chooseType(PicturePicker.CHOOSE_TYPE_SINGLE)
                .requestCode(TAKE_FROM_PICTURES_CODE)
                .start()
    }

    private fun takeFromCamera() {
        PermissionRequester(this).request(Manifest.permission.CAMERA)
                .o2Subscribe {
                    onNext { (granted, shouldShowRequestPermissionRationale, deniedPermissions) ->
                        XLog.info("granted:$granted , shouldShowRequest:$shouldShowRequestPermissionRationale, denied:$deniedPermissions")
                        if (!granted) {
                            O2DialogSupport.openAlertDialog(this@TaskWebViewActivity, "???????????????????????????????????????????????????????????????")
                        } else {
                            openCamera()
                        }
                    }
                }
    }


    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        //return-data false ????????????????????????????????????Bitmap ?????????????????????????????????
        intent.putExtra("return-data", false)
        //??????Uri ??????
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
        intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString())
        intent.putExtra("noFaceDetection", true)
        startActivityForResult(intent, TAKE_FROM_CAMERA_CODE)
    }



    private fun openFancyFilePicker(requestCode: Int) {
        FilePicker().withActivity(this).requestCode(requestCode)
                .chooseType(FilePicker.CHOOSE_TYPE_SINGLE)
                .start()
    }



    private fun uploadImage2FileStorageStart(filePath: String) {
        showLoadingDialog()
        if (imageUploadData != null) {
            mPresenter.upload2FileStorage(filePath, imageUploadData!!.referencetype, imageUploadData!!.reference)
        }else {
            finishLoading()
            XToast.toastShort(this, "?????????????????????????????????")
        }
    }

    //endregion

}
