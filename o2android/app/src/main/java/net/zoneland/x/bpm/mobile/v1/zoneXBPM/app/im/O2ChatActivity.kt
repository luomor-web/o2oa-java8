package net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.im

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.AudioFormat
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.provider.Settings
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.wugang.activityresult.library.ActivityResult
import com.zlw.main.recorderlib.RecordManager
import com.zlw.main.recorderlib.recorder.RecordConfig
import com.zlw.main.recorderlib.recorder.RecordHelper
import com.zlw.main.recorderlib.recorder.listener.RecordStateListener
import kotlinx.android.synthetic.main.activity_o2_chat.*
import net.muliba.fancyfilepickerlibrary.PicturePicker
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.O2SDKManager
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.R
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.base.BaseMVPActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.o2.organization.ContactPickerActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.o2.webview.LocalImageViewActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.core.component.adapter.CommonRecycleViewAdapter
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.core.component.adapter.CommonRecyclerViewHolder
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.bo.api.im.*
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.vo.ContactPickerResult
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.*
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.extension.go
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.extension.gone
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.extension.o2Subscribe
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.extension.visible
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.permission.PermissionRequester
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.widgets.dialog.O2DialogSupport
import java.io.File
import java.util.*


class O2ChatActivity : BaseMVPActivity<O2ChatContract.View, O2ChatContract.Presenter>(), O2ChatContract.View, View.OnTouchListener {

    companion object {
        const val con_id_key = "con_id_key"
        fun startChat(activity: Activity, conversationId: String) {
            val bundle = Bundle()
            bundle.putString(con_id_key, conversationId)
            activity?.go<O2ChatActivity>(bundle)
        }
    }


    override var mPresenter: O2ChatContract.Presenter = O2ChatPresenter()

    override fun layoutResId(): Int = R.layout.activity_o2_chat


    private val adapter: O2ChatMessageAdapter by lazy { O2ChatMessageAdapter() }
    private val emojiList = O2IM.im_emoji_hashMap.keys.toList().sortedBy { it }
    private val emojiAdapter: CommonRecycleViewAdapter<String> by lazy {
        object : CommonRecycleViewAdapter<String>(this, emojiList, R.layout.item_o2_im_chat_emoji) {
            override fun convert(holder: CommonRecyclerViewHolder?, t: String?) {
                if (t != null) {
                    holder?.setImageViewResource(R.id.image_item_o2_im_chat_emoji, O2IM.emojiResId(t))
                }
            }
        }
    }

    //
    private val defaultTitle = "????????????"
    private var page = 0

    private var conversationId = ""

    private var conversationInfo: IMConversationInfo? = null
    //????????????
    private var isAudioRecordCancel = false
    private var audioRecordTime = 0L
    //???????????????
    private val audioCountDownTimer: CountDownTimer by lazy {
        object : CountDownTimer(60 * 1000, 1000) {
            override fun onFinish() {
                XLog.debug("??????????????????")
                endRecordAudio()
            }

            override fun onTick(millisUntilFinished: Long) {
                val sec = ((millisUntilFinished + 15) / 1000)
                audioRecordTime = 60 - sec
                runOnUiThread {
                    val times = if (audioRecordTime > 9) {
                        "00:$audioRecordTime"
                    } else {
                        "00:0$audioRecordTime"
                    }
                    tv_o2_chat_audio_speak_duration.text = times
                }
                XLog.debug("?????????????????????$sec ???")
            }

        }
    }

    //media play
    private var mPlayer: MediaPlayer? = null

    //??????
    private val cameraImageUri: Uri by lazy { FileUtil.getUriFromFile(this, File(FileExtensionHelper.getCameraCacheFilePath())) }
    private val camera_result_code = 10240

    //????????????????????? ?????????
    private var canUpdate = false




//    private var mKeyboardHeight = 150 // ????????????????????????400


    override fun afterSetContentView(savedInstanceState: Bundle?) {
        // ????????????????????????????????????
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

        setupToolBar(defaultTitle, setupBackButton = true)

        conversationId = intent.getStringExtra(con_id_key) ?: ""
        if (TextUtils.isEmpty(conversationId)) {
            XToast.toastShort(this, "???????????????")
            finish()
        }
        //?????????????????????
        sr_o2_chat_message_layout.setOnRefreshListener {
            XLog.debug("????????????????????????????????????")
            getPageData()
        }
        rv_o2_chat_messages.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        rv_o2_chat_messages.adapter = adapter
        adapter.eventListener = object : O2ChatMessageAdapter.MessageEventListener {
            override fun resendClick(message: IMMessage) {
                mPresenter.sendIMMessage(message)//????????????
            }

            override fun playAudio(position: Int, msgBody: IMMessageBody) {
                XLog.debug("audio play position: $position")
                mPresenter.getFileFromNetOrLocal(position, msgBody)
            }

            override fun openOriginImage(position: Int, msgBody: IMMessageBody) {
                 mPresenter.getFileFromNetOrLocal(position, msgBody)
            }

            override fun openLocation(msgBody: IMMessageBody) {
                val location = O2LocationActivity.LocationData(msgBody.address, msgBody.addressDetail, msgBody.latitude, msgBody.longitude)
                val bundle = O2LocationActivity.showLocation(location)
                go<O2LocationActivity>(bundle)
            }
        }
        //???????????????????????????????????????
        cl_o2_chat_outside.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom) {
                scroll2Bottom()
            }
        }

        //???????????????
        rv_o2_chat_emoji_box.layoutManager = GridLayoutManager(this, 10)
        rv_o2_chat_emoji_box.adapter = emojiAdapter
        emojiAdapter.setOnItemClickListener { _, position ->
            val key = emojiList[position]
            XLog.debug(key)
            newEmojiMessage(key)
            //??????????????????
            mPresenter.readConversation(conversationId)
        }

        initListener()

        sr_o2_chat_message_layout.isRefreshing = true
        getPageData()

        //????????????
        initAudioRecord()

        registerBroadcast()
    }


    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.clear()
        if (canUpdate) {
            menuInflater.inflate(R.menu.menu_chat, menu)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when(item?.itemId) {
            R.id.menu_chat_update_title -> {
                updateTitle()
                return true
            }
            R.id.menu_chat_update_member -> {
                updateMembers()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateTitle() {
        val dialog = O2DialogSupport.openCustomViewDialog(this, "????????????", R.layout.dialog_name_modify) { dialog ->
            val text = dialog.findViewById<EditText>(R.id.dialog_name_editText_id)
            val content = text.text.toString()
            dialog.dismiss()
            if (TextUtils.isEmpty(content)) {
                XToast.toastShort(this@O2ChatActivity, "?????????????????????")
            } else {
                showLoadingDialog()
                mPresenter.updateConversationTitle(conversationId, content)
            }
        }
        val edit = dialog.findViewById<EditText>(R.id.dialog_name_editText_id)
        edit.hint = "??????????????????"
    }

    private fun updateMembers() {
        val users = conversationInfo?.personList ?: ArrayList<String>()
        ActivityResult.of(this)
                .className(ContactPickerActivity::class.java)
                .params(ContactPickerActivity.startPickerBundle(pickerModes = arrayListOf(ContactPickerActivity.personPicker), multiple = true, initUserList = users))
                .greenChannel().forResult { _, data ->
                    val result = data?.getParcelableExtra<ContactPickerResult>(ContactPickerActivity.CONTACT_PICKED_RESULT)
                    if (result != null && result.users.isNotEmpty()) {
                        val a = arrayListOf<String>()
                        a.addAll(result.users.map { it.distinguishedName })
                        if (!a.any { it == O2SDKManager.instance().distinguishedName }) {
                            a.add(O2SDKManager.instance().distinguishedName)
                        }
                        showLoadingDialog()
                        mPresenter.updateConversationPeople(conversationId, a)
                    }else {
                        XLog.debug("??????????????????????????????")
                    }
                }
    }


    override fun onDestroy() {
        super.onDestroy()
        if (mReceiver != null) {
            unregisterReceiver(mReceiver)
        }
        if (mPlayer != null) {
            mPlayer?.release()//????????????
            mPlayer = null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == camera_result_code) {
            //??????
            XLog.debug("??????//// ")
            newImageMessage(FileExtensionHelper.getCameraCacheFilePath())
        }
    }

    private var startY: Float = 0f
    private var isCancelRecord = false


    /**
     * ???????????????touch??????
     * ????????????
     * ????????????????????????
     * ??????????????????
     */
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        when(event?.action) {
            MotionEvent.ACTION_DOWN -> {
                startY = event.y
                XLog.debug("????????????............??????")
                updateRecordingDialogUI(R.mipmap.listener08, "???????????????????????????")
                recordingDialog?.show()
                startRecordAudio()
            }
            MotionEvent.ACTION_UP -> {
                XLog.debug("?????????................??????")
                if (isCancelRecord) {
                    XLog.debug("???????????????.....")
                    cancelRecordAudio()
                }else {
                    XLog.debug("???????????????.....")
                    endRecordAudio()
                }
                recordingDialog?.dismiss()
            }
            MotionEvent.ACTION_MOVE -> {
                val moveY = event.y
                if (startY - moveY > 100) {
                    isCancelRecord = true
                    updateRecordingDialogUI(R.mipmap.chat_audio_record_cancel, "???????????????????????????")
                }
                if (startY - moveY < 20) {
                    isCancelRecord = false
                    updateRecordingDialogUI(R.mipmap.listener08, "???????????????????????????")
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                XLog.debug("?????????................??????")
                cancelRecordAudio()
                recordingDialog?.dismiss()
            }
        }
        return true
    }

    private var tvPrompt: TextView? = null
    private var ivLoad:ImageView? = null
    private var recordingDialog: AlertDialog? = null
    private fun recordingDialog() : AlertDialog {
        val dialogBuilder = AlertDialog.Builder(this, R.style.DialogManage)
        dialogBuilder.setCancelable(false)
        val view: View = LayoutInflater.from(this).inflate(R.layout.dialog_voice_speak, null)
        tvPrompt = view.findViewById<TextView>(R.id.tv_prompt)
        ivLoad = view.findViewById<ImageView>(R.id.iv_load)
        dialogBuilder.setView(view)
        val dialog = dialogBuilder.create()
        dialog.window?.setBackgroundDrawable(BitmapDrawable())
        return dialog
    }
    private fun updateRecordingDialogUI(resId: Int, prompt: String?) {
        if (null != tvPrompt && null != ivLoad) {
            tvPrompt?.text = prompt
            ivLoad?.setImageResource(resId)
        }
    }


    override fun updateSuccess(info: IMConversationInfo) {
        hideLoadingDialog()
        this.conversationInfo?.title = info.title
        updateToolbarTitle(info.title)
        this.conversationInfo?.personList = info.personList
    }

    override fun updateFail(msg: String) {
        hideLoadingDialog()
        XToast.toastShort(this, msg)
    }

    override fun conversationInfo(info: IMConversationInfo) {
        conversationInfo = info
        if (conversationInfo?.adminPerson == O2SDKManager.instance().distinguishedName) {
            canUpdate = true
            invalidateOptionsMenu()
        }
        //
        var title = defaultTitle
        if (O2IM.conversation_type_single == conversationInfo?.type) {
            val persons = conversationInfo?.personList
            if (persons != null && persons.isNotEmpty()) {
                val person = persons.firstOrNull { it != O2SDKManager.instance().distinguishedName }
                if (person != null) {
                    title = person.substring(0, person.indexOf("@"))
                }
            }
        } else if (O2IM.conversation_type_group == conversationInfo?.type) {
            title = conversationInfo?.title ?: defaultTitle
        }
        updateToolbarTitle(title)
    }

    override fun conversationGetFail() {
        XToast.toastShort(this, "???????????????????????????")
        finish()
    }

    override fun backPageMessages(list: List<IMMessage>) {
        sr_o2_chat_message_layout.isRefreshing = false
        if (list.isNotEmpty()) {
            page++
            adapter.addPageMessage(list)
        }
        //????????? ???????????????
        if (page == 1) {
            scroll2Bottom()
        }
    }

    override fun sendMessageSuccess(id: String) {
        //???????????????loading??????
        adapter.sendMessageSuccess(id)
    }

    override fun sendFail(id: String) {
        //???????????????loading?????? ??????????????????
        adapter.sendMessageFail(id)
    }

    override fun localFile(filePath: String, msgType: String, position: Int) {
        XLog.debug("local file :$filePath type:$msgType")
        when (msgType) {
            MessageType.audio.key -> {
                playAudio2(filePath, position)
            }
            MessageType.image.key -> {
                //????????????
                go<LocalImageViewActivity>(LocalImageViewActivity.startBundle(filePath))
            }
            else -> AndroidUtils.openFileWithDefaultApp(this@O2ChatActivity, File(filePath))
        }

    }

    override fun downloadFileFail(msg: String) {
        XToast.toastShort(this, msg)
    }

    /**
     * ??????
     */
    private fun initListener() {
        et_o2_chat_input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s != null && !TextUtils.isEmpty(s)) {
                    btn_o2_chat_send.visible()
                    btn_o2_chat_emotion.gone()
                } else {
                    btn_o2_chat_emotion.visible()
                    btn_o2_chat_send.gone()
                }
            }
        })
        et_o2_chat_input.setOnClickListener {
            rv_o2_chat_emoji_box_out.postDelayed({
                rv_o2_chat_emoji_box.gone()
                tv_o2_chat_audio_send_box.gone()
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }, 250)
        }
        rv_o2_chat_emoji_box_out.setKeyboardListener { isActive, keyboardHeight ->
            if (isActive) { // ???????????????
//                if (mKeyboardHeight != keyboardHeight) { // ??????????????????????????????emojiView???????????????????????????onGlobalLayoutChanged?????????onKeyboardStateChanged???????????????
//                    mKeyboardHeight = keyboardHeight
//                    initEmojiView() // ?????????????????????????????????emojiView??????????????????????????????????????????emojiView?????????????????????????????????
//                }
                if (rv_o2_chat_emoji_box.visibility == View.VISIBLE) { // ?????????????????????
                    rv_o2_chat_emoji_box.gone()
                }
                if (tv_o2_chat_audio_send_box.visibility == View.VISIBLE) { // ?????????????????????
                    tv_o2_chat_audio_send_box.gone()
                }
            }
        }
        btn_o2_chat_emotion.setOnClickListener {
            //???????????????
            tv_o2_chat_audio_send_box.gone()
            if (rv_o2_chat_emoji_box_out.isKeyboardActive) { //??????????????????
                if (rv_o2_chat_emoji_box.visibility == View.GONE) {
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING) //  ?????????????????????????????????emojiView??????
                    val imm = it.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(et_o2_chat_input.applicationWindowToken, 0)
                    rv_o2_chat_emoji_box.visibility = View.VISIBLE
                } else {
                    rv_o2_chat_emoji_box.visibility = View.GONE
                    val imm = it.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(et_o2_chat_input.applicationWindowToken, 0)
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                }
            } else {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                if (rv_o2_chat_emoji_box.visibility == View.GONE) {
                    rv_o2_chat_emoji_box.visibility = View.VISIBLE
                } else {
                    rv_o2_chat_emoji_box.visibility = View.GONE
                }
            }

        }
        btn_o2_chat_send.setOnClickListener {
            sendTextMessage()
        }

        //bottom toolbar
        image_o2_chat_audio_speak_btn.setOnTouchListener(this)
        ll_o2_chat_audio_btn.setOnClickListener {
            //?????????????????????
            PermissionRequester(this@O2ChatActivity)
                    .request(Manifest.permission.RECORD_AUDIO)
                    .o2Subscribe {
                        onNext { (granted, _, _) ->
                            if (!granted){
                                O2DialogSupport.openAlertDialog(this@O2ChatActivity, "????????????????????????, ?????????", { permissionSetting() })
                            }
                        }
                        onError { e, _ ->
                            XLog.error("", e)
                        }
                    }
            //???????????????
            rv_o2_chat_emoji_box.gone()
            if (rv_o2_chat_emoji_box_out.isKeyboardActive) { //??????????????????
                if (tv_o2_chat_audio_send_box.visibility == View.GONE) {
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING) //  ?????????????????????????????????emojiView??????
                    val imm = it.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(et_o2_chat_input.applicationWindowToken, 0)
                    tv_o2_chat_audio_send_box.visibility = View.VISIBLE
                } else {
                    tv_o2_chat_audio_send_box.visibility = View.GONE
                    val imm = it.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(et_o2_chat_input.applicationWindowToken, 0)
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                }
            } else {
                if (tv_o2_chat_audio_send_box.visibility == View.GONE) {
                    tv_o2_chat_audio_send_box.visibility = View.VISIBLE
                } else {
                    tv_o2_chat_audio_send_box.visibility = View.GONE
                }
            }
        }
        ll_o2_chat_album_btn.setOnClickListener {
            PicturePicker()
                    .withActivity(this)
                    .chooseType(PicturePicker.CHOOSE_TYPE_SINGLE).forResult { files ->
                        if (files.isNotEmpty()) {
                            newImageMessage(files[0])
                        }
                    }
        }
        ll_o2_chat_camera_btn.setOnClickListener {
            PermissionRequester(this@O2ChatActivity).request(Manifest.permission.CAMERA)
                    .o2Subscribe {
                        onNext {  (granted, _, _) ->
                            if (!granted){
                                O2DialogSupport.openAlertDialog(this@O2ChatActivity, "??????????????????, ?????????", { permissionSetting() })
                            } else {
                                openCamera()
                            }
                        }
                        onError { e, _ ->
                            XLog.error("", e)
                        }
                    }
        }
        ll_o2_chat_location_btn.setOnClickListener {
            ActivityResult.of(this)
                    .className(O2LocationActivity::class.java)
                    .params(O2LocationActivity.startChooseLocation())
                    .greenChannel()
                    .forResult { resultCode, data ->
                        if (resultCode == Activity.RESULT_OK) {
                            val location = data.extras?.getParcelable<O2LocationActivity.LocationData>(O2LocationActivity.RESULT_LOCATION_KEY)
                            if (location != null) {
                                newLocationMessage(location)
                            }
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
        startActivityForResult(intent, camera_result_code)
    }


    private fun permissionSetting() {
        val packageUri = Uri.parse("package:$packageName")
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
    }

    // ????????????????????????
//    private fun initEmojiView() {
//        val layoutParams = rv_o2_chat_emoji_box.layoutParams
//        layoutParams.height = mKeyboardHeight
//        rv_o2_chat_emoji_box.layoutParams = layoutParams
//    }

    /**
     * ???????????????????????????
     */
    private fun initAudioRecord() {
        RecordManager.getInstance().changeFormat(RecordConfig.RecordFormat.MP3)
        RecordManager.getInstance().changeRecordConfig(RecordManager.getInstance().recordConfig.setSampleRate(16000))
        RecordManager.getInstance().changeRecordConfig(RecordManager.getInstance().recordConfig.setEncodingConfig(AudioFormat.ENCODING_PCM_8BIT))
        RecordManager.getInstance().changeRecordDir(FileExtensionHelper.getXBPMTempFolder() + File.separator)
        RecordManager.getInstance().setRecordStateListener(object : RecordStateListener {
            override fun onError(error: String?) {
                XLog.error("????????????, $error")
            }

            override fun onStateChange(state: RecordHelper.RecordState?) {
                when (state) {
                    RecordHelper.RecordState.IDLE -> XLog.debug("??????????????? ????????????")
                    RecordHelper.RecordState.RECORDING -> {
                        XLog.debug("??????????????? ?????????")
                        audioCountDownTimer.start()
                    }
                    RecordHelper.RecordState.PAUSE -> XLog.debug("??????????????? ?????????")
                    RecordHelper.RecordState.STOP -> XLog.debug("??????????????? ????????????")
                    RecordHelper.RecordState.FINISH -> XLog.debug("??????????????? ????????????????????????????????????")
                }

            }
        })
        RecordManager.getInstance().setRecordResultListener { result ->
            if (result == null) {
                runOnUiThread { XToast.toastShort(this@O2ChatActivity, "???????????????") }
            } else {
                XLog.debug("???????????? ???????????? ${result.path} ??? ???????????????$isAudioRecordCancel, ???????????????$audioRecordTime")
                if (audioRecordTime < 1) {
                    runOnUiThread {
                        XToast.toastShort(this@O2ChatActivity, "?????????????????????")
                    }
                } else {
                    newAudioMessage(result.path, "$audioRecordTime")
                }
            }
        }
        recordingDialog = recordingDialog()
    }

    /**
     * ????????????
     */
    private fun startRecordAudio() {
        XLog.debug("????????????????????????")
        audioRecordTime = 0L
        RecordManager.getInstance().start()
        tv_o2_chat_audio_speak_title.text = resources.getText(R.string.activity_im_audio_speak_cancel)
    }

    /**
     * ????????????
     */
    private fun endRecordAudio() {
        XLog.debug("????????????????????????")
        audioCountDownTimer.cancel()
        RecordManager.getInstance().stop()
        tv_o2_chat_audio_speak_title.text = resources.getText(R.string.activity_im_audio_speak)
        tv_o2_chat_audio_speak_duration.text = ""
    }

    /**
     * ????????????
     */
    private fun cancelRecordAudio() {
        XLog.debug("???????????????????????????")
        isAudioRecordCancel = true
        audioCountDownTimer.cancel()
        RecordManager.getInstance().stop()
        tv_o2_chat_audio_speak_title.text = resources.getText(R.string.activity_im_audio_speak)
        tv_o2_chat_audio_speak_duration.text = ""
    }

    private fun playAudio2(filePath: String, position: Int) {
        if (mPlayer != null) {
            mPlayer?.release()
            mPlayer = null
        }
        XLog.debug("uri : $filePath")
        val uri = Uri.fromFile(File(filePath))
        mPlayer = MediaPlayer.create(this@O2ChatActivity, uri)
        mPlayer?.setOnCompletionListener {
            XLog.debug("???????????????")

        }
        mPlayer?.start()
    }


    /**
     * ??????????????????
     */
    private fun getPageData() {
        mPresenter.getConversation(conversationId)
        mPresenter.getMessage(page + 1, conversationId)
        //??????????????????
        mPresenter.readConversation(conversationId)
    }

    /**
     * ?????????????????????
     */
    private fun scroll2Bottom() {
        rv_o2_chat_messages.scrollToPosition(adapter.lastPosition())
    }

    /**
     * ????????????
     */
    private fun sendTextMessage() {
        val text = et_o2_chat_input.text.toString()
        if (!TextUtils.isEmpty(text)) {
            et_o2_chat_input.setText("")
            newTextMessage(text)
        }
        //??????????????????
        mPresenter.readConversation(conversationId)
    }

    /**
     * ?????????????????? ?????????
     */
    private fun newTextMessage(text: String) {
        val time = DateHelper.now()
        val body = IMMessageBody(type = MessageType.text.key, body = text)
        val bodyJson = O2SDKManager.instance().gson.toJson(body)
        XLog.debug("body: $bodyJson")
        val uuid = UUID.randomUUID().toString()
        val message = IMMessage(uuid, conversationId, bodyJson,
                O2SDKManager.instance().distinguishedName, time, 1)
        adapter.addMessage(message)
        mPresenter.sendIMMessage(message)//??????????????????
        scroll2Bottom()
    }

    /**
     * ??????????????????
     */
    private fun newEmojiMessage(emoji: String) {
        val time = DateHelper.now()
        val body = IMMessageBody(type = MessageType.emoji.key, body = emoji)
        val bodyJson = O2SDKManager.instance().gson.toJson(body)
        XLog.debug("body: $bodyJson")
        val uuid = UUID.randomUUID().toString()
        val message = IMMessage(uuid, conversationId, bodyJson,
                O2SDKManager.instance().distinguishedName, time, 1)
        adapter.addMessage(message)
        mPresenter.sendIMMessage(message)//??????????????????
        scroll2Bottom()
    }

    /**
     * ?????????????????? ?????????
     */
    private fun newAudioMessage(filePath: String, duration: String) {
        val time = DateHelper.now()
        val body = IMMessageBody(type = MessageType.audio.key, body = MessageBody.audio.body,
                fileTempPath = filePath, audioDuration = duration)
        val bodyJson = O2SDKManager.instance().gson.toJson(body)
        XLog.debug("body: $bodyJson")
        val uuid = UUID.randomUUID().toString()
        val message = IMMessage(uuid, conversationId, bodyJson,
                O2SDKManager.instance().distinguishedName, time, 1)
        adapter.addMessage(message)
        mPresenter.sendIMMessage(message)//??????????????????
        scroll2Bottom()
    }

    /**
     * ???????????? ?????? ?????????
     */
    private fun newImageMessage(filePath: String) {
        val time = DateHelper.now()
        val body = IMMessageBody(type = MessageType.image.key, body = MessageBody.image.body, fileTempPath = filePath)
        val bodyJson = O2SDKManager.instance().gson.toJson(body)
        XLog.debug("body: $bodyJson")
        val uuid = UUID.randomUUID().toString()
        val message = IMMessage(uuid, conversationId, bodyJson,
                O2SDKManager.instance().distinguishedName, time, 1)
        adapter.addMessage(message)
        mPresenter.sendIMMessage(message)//??????????????????
        scroll2Bottom()
    }

    /**
     * ???????????? ???????????????
     */
    private fun newLocationMessage(location: O2LocationActivity.LocationData) {
        val time = DateHelper.now()
        val body = IMMessageBody(type = MessageType.location.key, body = MessageBody.location.body,
                address = location.address, addressDetail = location.addressDetail,
                latitude = location.latitude, longitude = location.longitude)
        val bodyJson = O2SDKManager.instance().gson.toJson(body)
        XLog.debug("body: $bodyJson")
        val uuid = UUID.randomUUID().toString()
        val message = IMMessage(uuid, conversationId, bodyJson,
                O2SDKManager.instance().distinguishedName, time, 1)
        adapter.addMessage(message)
        mPresenter.sendIMMessage(message)//??????????????????
        scroll2Bottom()
    }

    /**
     * ???????????????
     */
    private fun receiveMessage(message: IMMessage) {
        adapter.addMessage(message)
        scroll2Bottom()
        //??????????????????
        mPresenter.readConversation(conversationId)
    }


    var mReceiver: IMMessageReceiver? = null
    private fun registerBroadcast() {
        mReceiver = IMMessageReceiver()
        val filter = IntentFilter(O2IM.IM_Message_Receiver_Action)
        registerReceiver(mReceiver, filter)
    }


    inner class IMMessageReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val body = intent?.getStringExtra(O2IM.IM_Message_Receiver_name)
            if (body != null && body.isNotEmpty()) {
                XLog.debug("?????????im??????, $body")
                try {
                    val message = O2SDKManager.instance().gson.fromJson<IMMessage>(body, IMMessage::class.java)
                    if (message.conversationId == conversationId) {
                        receiveMessage(message)
                    }
                } catch (e: Exception) {
                    XLog.error("", e)
                }

            }
        }

    }
}
