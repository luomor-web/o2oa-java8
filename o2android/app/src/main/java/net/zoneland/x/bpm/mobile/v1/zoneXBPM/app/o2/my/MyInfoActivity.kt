package net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.o2.my


import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import kotlinx.android.synthetic.main.activity_my_info.*
import net.muliba.fancyfilepickerlibrary.PicturePicker
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.O2SDKManager
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.R
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.base.BaseMVPActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.o2.login.LoginActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.core.component.api.APIAddressHelper
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.core.component.enums.GenderTypeEnums
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.bo.api.main.person.PersonJson
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.*
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.extension.*
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.imageloader.O2ImageLoaderManager
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.imageloader.O2ImageLoaderOptions
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.permission.PermissionRequester
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.widgets.CommonMenuPopupWindow
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.widgets.dialog.O2DialogSupport
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File


class MyInfoActivity : BaseMVPActivity<MyInfoContract.View, MyInfoContract.Presenter>(), MyInfoContract.View, View.OnClickListener {

    companion object {
        val TAKE_FROM_PICTURES_CODE = 1
        val TAKE_FROM_CAMERA_CODE = 2
        val CLIP_AVATAR_ACTIVITY_CODE = 3
        val REQUEST_CODE_ASK_PERMISSIONS = 123
    }

    override var mPresenter: MyInfoContract.Presenter = MyInfoPresenter()

    val avatarMenuList: ArrayList<String> = arrayListOf("??????", "?????????????????????")
    val avatarMenu: CommonMenuPopupWindow by lazy { CommonMenuPopupWindow(avatarMenuList, this) }

    var person: PersonJson? = null
    val cameraImageUri: Uri by lazy { FileUtil.getUriFromFile(this, File(FileExtensionHelper.getCameraCacheFilePath())) }
    var isEdit = false

    //?????????
    override fun beforeSetContentView() {
        super.beforeSetContentView()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
    }

    override fun afterSetContentView(savedInstanceState: Bundle?) {
        //????????????????????????
        SDCardHelper.generateNewFile(FileExtensionHelper.getCameraCacheFilePath())

        image_myInfo_back.setOnClickListener(this)
        image_myInfo_avatar.setOnClickListener(this)
//        myInfo_logout_btn_id.setOnClickListener(this)
        tv_myInfo_edit_save_btn.setOnClickListener(this)
        linear_myInfo_gender_men_button.setOnClickListener(this)
        linear_myInfo_gender_women_button.setOnClickListener(this)
        avatarMenu.setOnDismissListener { ZoneUtil.lightOn(this@MyInfoActivity) }
        avatarMenu.onMenuItemClickListener = object : CommonMenuPopupWindow.OnMenuItemClickListener {
            override fun itemClick(position: Int) {
                when (position) {
                    0 -> takeFromCamera()
                    1 -> takeFromPictures()
                }
                avatarMenu.dismiss()
            }
        }

        showLoadingDialog()
        mPresenter.loadMyInfo()
        showAvatar()
    }

    override fun layoutResId(): Int {
        return R.layout.activity_my_info
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isEdit) {
                switchStatus(false)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                TAKE_FROM_CAMERA_CODE -> startClipAvatar(cameraImageUri)

                TAKE_FROM_PICTURES_CODE -> {
                    XLog.debug("choose from pictures ...")
                    data?.let {
                        val result = it.extras?.getString(PicturePicker.FANCY_PICTURE_PICKER_SINGLE_RESULT_KEY, "")
                        if (!TextUtils.isEmpty(result)){
                            val uri = Uri.fromFile(File(result!!))
                            startClipAvatar(uri)
                        }
                    }
                }

                CLIP_AVATAR_ACTIVITY_CODE -> {
                    data?.let {
                        val url = it.extras?.getString("clipAvatarFilePath")
                        XLog.debug("back Myinfo avatar uri : $url ")
                        modifyAvatar2Remote(url)
                    }
                }
            }
        }


    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.tv_myInfo_edit_save_btn -> switchTheStatusAndUpdateTheData()
            R.id.image_myInfo_back -> {
                if (isEdit) {
                    switchStatus(false)
                }else {
                    finish()
                }
            }
            R.id.image_myInfo_avatar -> modifyAvatarMenuShow()
//            R.id.myInfo_logout_btn_id -> logout()
            R.id.linear_myInfo_gender_men_button -> {
                if (isEdit) {
                    image_myInfo_gender_men_edit.visibility = View.VISIBLE
                    image_myInfo_gender_women_edit.visibility = View.GONE
                }
            }
            R.id.linear_myInfo_gender_women_button -> {
                if (isEdit) {
                    image_myInfo_gender_men_edit.visibility = View.GONE
                    image_myInfo_gender_women_edit.visibility = View.VISIBLE
                }
            }
        }
    }


    override fun loadMyInfoSuccess(personal: PersonJson) {
        hideLoadingDialog()
        person = personal
        switchStatus(false)
        tv_myInfo_name.text = personal.name
        myInfo_officePhone_value_id.text = personal.officePhone
        edit_myInfo_officePhone.setText(personal.officePhone)
        tv_myInfo_sign_edit.text = personal.signature
        tv_myInfo_sign.text = personal.signature
        edit_myInfo_sign.setText(personal.signature)
        myInfo_mobile_value_id.text = personal.mobile
        edit_myInfo_mobile.setText(personal.mobile)
        myInfo_email_value_id.text = personal.mail
        edit_myInfo_email.setText(personal.mail)
        myInfo_qq_value_id.text = personal.qq
        edit_myInfo_qq.setText(personal.qq)
        myInfo_weixin_value_id.text = personal.weixin
        edit_myInfo_weixin.setText(personal.weixin)
        if (GenderTypeEnums.MALE.key == personal.genderType) {
            image_myInfo_gender_men.setImageResource(R.mipmap.icon_gender_men_enable_50dp)
            image_myInfo_gender_women.setImageResource(R.mipmap.icon_gender_women_disable_50dp)
        } else {
            image_myInfo_gender_men.setImageResource(R.mipmap.icon_gender_men_disable_50dp)
            image_myInfo_gender_women.setImageResource(R.mipmap.icon_gender_women_enable_50dp)
        }
        scroll_myInfo.scrollTo(0, 0)
    }

    override fun loadMyInfoFail() {
        hideLoadingDialog()
        XToast.toastShort(this, "???????????????????????????")
        finish()
    }

    override fun updateMyInfoFail() {
        hideLoadingDialog()
        XToast.toastShort(this, "???????????????????????????")
        switchStatus(false)
    }

    override fun updateMyIcon(f: Boolean) {
        hideLoadingDialog()
        if (f) {
            doAsync {
                //??????1???
                XLog.debug("sleep 1 second")
                Thread.sleep(1000L)
                uiThread {
                    showAvatar()
                }
            }
        }
    }

    override fun logoutSuccess() {
        O2SDKManager.instance().logoutCleanCurrentPerson()
        goThenKill<LoginActivity>()
    }

    override fun logoutFail() {
        O2SDKManager.instance().logoutCleanCurrentPerson()
        goThenKill<LoginActivity>()
    }

    private fun switchStatus(flag: Boolean) {
        if (flag) {
            tv_myInfo_sign_edit.visibility = View.GONE
            edit_myInfo_sign.visibility = View.VISIBLE
            myInfo_officePhone_value_id.visibility = View.GONE
            edit_myInfo_officePhone.visibility = View.VISIBLE
            myInfo_mobile_value_id.visibility = View.GONE
            edit_myInfo_mobile.visibility = View.VISIBLE
            myInfo_email_value_id.visibility = View.GONE
            edit_myInfo_email.visibility = View.VISIBLE
            myInfo_qq_value_id.visibility = View.GONE
            edit_myInfo_qq.visibility = View.VISIBLE
            myInfo_weixin_value_id.gone()
            edit_myInfo_weixin.visible()
            if (GenderTypeEnums.MALE.key == person?.genderType) {
                image_myInfo_gender_men_edit.visibility = View.VISIBLE
                image_myInfo_gender_women_edit.visibility = View.GONE
            } else {
                image_myInfo_gender_men_edit.visibility = View.GONE
                image_myInfo_gender_women_edit.visibility = View.VISIBLE
            }
            image_myInfo_gender_men.setImageResource(R.mipmap.icon_gender_men_enable_50dp)
            image_myInfo_gender_women.setImageResource(R.mipmap.icon_gender_women_enable_50dp)
            image_myInfo_edit_avatar.visibility = View.VISIBLE
//            myInfo_logout_btn_id.visibility = View.INVISIBLE
        } else {
            tv_myInfo_sign_edit.visibility = View.VISIBLE
            edit_myInfo_sign.visibility = View.GONE
            myInfo_officePhone_value_id.visibility = View.VISIBLE
            edit_myInfo_officePhone.visibility = View.GONE
            myInfo_mobile_value_id.visibility = View.VISIBLE
            edit_myInfo_mobile.visibility = View.GONE
            myInfo_email_value_id.visibility = View.VISIBLE
            edit_myInfo_email.visibility = View.GONE
            myInfo_qq_value_id.visibility = View.VISIBLE
            edit_myInfo_qq.visibility = View.GONE
            myInfo_weixin_value_id.visible()
            edit_myInfo_weixin.gone()
            image_myInfo_gender_men_edit.visibility = View.GONE
            image_myInfo_gender_women_edit.visibility = View.GONE
            image_myInfo_edit_avatar.visibility = View.GONE
//            myInfo_logout_btn_id.visibility = View.VISIBLE
        }
        isEdit = flag
        refreshMenu()
    }


    private fun modifyAvatar2Remote(filePath: String?) {
        try {
            XLog.debug("in modifyAvatar2Remote...... $filePath")
            showLoadingDialog()
            mPresenter.updateMyIcon(File(filePath))
        } catch (e: Exception) {
            XLog.error("??????????????????", e)
            hideLoadingDialog()
        }
    }

    private fun startClipAvatar(pictureUri: Uri) {
//        val intent = Intent(this, ClipAvatarActivity::class.java)
//        intent.putExtra("avatarUri", pictureUri)
//        startActivityForResult(intent, CLIP_AVATAR_ACTIVITY_CODE)//??????????????????activity ???????????????????????????
        goWithRequestCode<ClipAvatarActivity>(ClipAvatarActivity.startWithBundle(pictureUri), CLIP_AVATAR_ACTIVITY_CODE)
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
                            O2DialogSupport.openAlertDialog(this@MyInfoActivity, "???????????????????????????????????????????????????????????????")
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


    private fun refreshMenu() {
        if (isEdit) {
            tv_myInfo_edit_save_btn.text = getString(R.string.menu_save)
        } else {
            tv_myInfo_edit_save_btn.text = getString(R.string.menu_edit)
        }
    }

    private fun logout() {
        O2DialogSupport.openConfirmDialog(this, "???????????????????????????", {
            mPresenter.logout()
        })
    }

    private fun modifyAvatarMenuShow() {
        avatarMenu.showAtLocation(image_myInfo_avatar, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 0)
        ZoneUtil.lightOff(this)

    }

    private fun switchTheStatusAndUpdateTheData() {
        if (isEdit) {

            if (person != null) {
                person?.name = tv_myInfo_name.text.toString()
                person?.signature = edit_myInfo_sign.text.toString()
                person?.mobile = edit_myInfo_mobile.text.toString()
                person?.mail = edit_myInfo_email.text.toString()
                person?.qq = edit_myInfo_qq.text.toString()
                person?.officePhone = edit_myInfo_officePhone.text.toString()
                person?.weixin = edit_myInfo_weixin.text.toString()
                if (View.VISIBLE == image_myInfo_gender_men_edit.visibility) {
                    person?.genderType = GenderTypeEnums.MALE.key
                } else {
                    person?.genderType = GenderTypeEnums.FEMALE.key
                }
                showLoadingDialog()
                mPresenter.updateMyInfo(person!!)
            } else {
                XLog.error("my info  is null.......")
                XToast.toastShort(this, "???????????????????????????????????????")
            }

        } else {
            switchStatus(true)
        }

    }

    private fun showAvatar() {
        //??????
        val url = APIAddressHelper.instance().getPersonAvatarUrlWithId(O2SDKManager.instance().distinguishedName)
        O2ImageLoaderManager.instance().showImage(image_myInfo_avatar, url, O2ImageLoaderOptions(isSkipCache = true))
    }
}
