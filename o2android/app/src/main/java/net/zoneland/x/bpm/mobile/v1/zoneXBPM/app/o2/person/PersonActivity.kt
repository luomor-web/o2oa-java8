package net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.o2.person


import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import kotlinx.android.synthetic.main.activity_person_info.*
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.O2SDKManager
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.R
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.base.BaseMVPActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.im.O2ChatActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.core.component.api.APIAddressHelper
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.core.component.enums.GenderTypeEnums
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.bo.api.im.IMConversationInfo
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.bo.api.main.person.PersonJson
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.AndroidUtils
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.XLog
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.XToast
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.ZoneUtil
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.extension.gone
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.extension.makeCallDial
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.extension.visible
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.imageloader.O2ImageLoaderManager
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.imageloader.O2ImageLoaderOptions
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.widgets.CommonMenuPopupWindow
import org.jetbrains.anko.email
import org.jetbrains.anko.sendSMS


class PersonActivity : BaseMVPActivity<PersonContract.View, PersonContract.Presenter>(), PersonContract.View, View.OnClickListener {
    override var mPresenter: PersonContract.Presenter = PersonPresenter()
    override fun layoutResId(): Int = R.layout.activity_person_info

    companion object {
        val PERSON_NAME_KEY = "name"

        fun startBundleData(person: String): Bundle {
            val bundle = Bundle()
            bundle.putString(PERSON_NAME_KEY, person)
            return bundle
        }
    }

    var loadedPersonId = ""//?????????id??????
    var loadedPersonDN = ""//?????????id??????
    var personId = ""
    var genderName = ""
    var hasCollection = false
    val mobileMenuItemList: ArrayList<String> = arrayListOf("????????????", "????????????", "??????")
    val mobileClickMenu: CommonMenuPopupWindow by lazy { CommonMenuPopupWindow(mobileMenuItemList, this) }
    val emailMenuItemList: ArrayList<String> = arrayListOf("????????????","??????")
    val emailClickMenu: CommonMenuPopupWindow by lazy { CommonMenuPopupWindow(emailMenuItemList, this) }
//    private var canTalkTo = false

    override fun afterSetContentView(savedInstanceState: Bundle?) {
        personId = intent.extras?.getString(PERSON_NAME_KEY, "")?:""
        if (TextUtils.isEmpty(personId)) {
            XToast.toastShort(this, "??????????????????????????????????????????????????????")
            finish()
            return
        }

        linear_person_mobile_button.setOnClickListener(this)
        linear_person_email_button.setOnClickListener(this)
        linear_person_collection_button.setOnClickListener(this)
        image_person_back.setOnClickListener(this)
        btn_begin_talk.setOnClickListener(this)

        showLoadingDialog()
        mPresenter.loadPersonInfo(personId)
        mPresenter.isUsuallyPerson(O2SDKManager.instance().distinguishedName, personId)
    }


    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.linear_person_mobile_button -> callPhone()
            R.id.linear_person_email_button -> sendEmail()
            R.id.linear_person_collection_button -> usuallyBtnClick()
            R.id.image_person_back -> finish()
            R.id.btn_begin_talk -> {
                // ????????????
                if (!TextUtils.isEmpty(loadedPersonDN) && O2SDKManager.instance().distinguishedName != loadedPersonDN) {
                    startTalk()
                }else {

                }
//                if (O2App.instance._JMIsLogin()) {
//                    if (canTalkTo && O2SDKManager.instance().cId != loadedPersonId) {
//                        val intent = Intent(this, ChatActivity::class.java)
//                        val user = O2App.instance._JMMyUserInfo()
//                        val name = tv_person_name.text.toString()
//                        XLog.info("current user: ${user?.userName}, ${user?.nickname}, ${user?.appKey}")
//                        XLog.info("to user: $loadedPersonId, $name")
//                        intent.putExtra(JIMConstant.CONV_TITLE, name)
//                        intent.putExtra(JIMConstant.TARGET_ID, loadedPersonId)
//                        intent.putExtra(JIMConstant.TARGET_APP_KEY, O2App.instance.JM_IM_APP_KEY)
//                        startActivity(intent)
//                    }else {
//                        XToast.toastShort(this, "?????????????????????????????????????????????????????????")
//                    }
//
//                }else {
//                    XToast.toastShort(this, "??????????????????????????????IM???????????????")
//                }
            }
        }
    }

    private fun startTalk() {
        mPresenter.startSingleTalk(loadedPersonDN)
    }

    private fun usuallyBtnClick() {
        if (O2SDKManager.instance().distinguishedName == loadedPersonDN) {
            XLog.debug("??????????????????????????????$personId")
            return
        }
        if (hasCollection) {
            mPresenter.deleteUsuallyPerson(O2SDKManager.instance().distinguishedName, personId)
            image_person_collection.setImageResource(R.mipmap.icon_collection_disable_50dp)
            tv_person_collection_text.text = getString(R.string.person_collect)
            hasCollection = false
        } else {
            val mobile = tv_person_mobile.text.toString()
            val name = tv_person_name.text.toString()
            mPresenter.collectionUsuallyPerson(O2SDKManager.instance().distinguishedName, personId, O2SDKManager.instance().cName, name, genderName, mobile)
            image_person_collection.setImageResource(R.mipmap.icon_collection_enable_50dp)
            tv_person_collection_text.text = getString(R.string.person_collect_cancel)
            hasCollection = true
        }
    }

    private fun sendEmail() {
        val emailAddress = tv_person_email.text.toString()
        if (TextUtils.isEmpty(emailAddress)) {
            return
        }
        emailClickMenu.setOnDismissListener { ZoneUtil.lightOn(this@PersonActivity) }
        emailClickMenu.onMenuItemClickListener = object : CommonMenuPopupWindow.OnMenuItemClickListener {
            override fun itemClick(position: Int) {
                when(position){
                    0 -> email(emailAddress)
                    1 ->  {
                        AndroidUtils.copyTextToClipboard(emailAddress, this@PersonActivity)
                        XToast.toastShort(this@PersonActivity, "???????????????????????????")
                    }
                }
                emailClickMenu.dismiss()
            }
        }
        emailClickMenu.showAtLocation(main_content, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 0)
        ZoneUtil.lightOff(this)
    }

    private fun callPhone() {
        val phone = tv_person_mobile.text.toString()
        XLog.debug("???????????????$phone")
        if (TextUtils.isEmpty(phone)) {
            return
        }
        mobileClickMenu.setOnDismissListener { ZoneUtil.lightOn(this@PersonActivity) }
        mobileClickMenu.onMenuItemClickListener = object : CommonMenuPopupWindow.OnMenuItemClickListener {
            override fun itemClick(position: Int) {
                when (position) {
                    0 -> makeCallDial(phone)
                    1 -> sendSMS(phone)
                    2 -> {
                        AndroidUtils.copyTextToClipboard(phone, this@PersonActivity)
                        XToast.toastShort(this@PersonActivity, "???????????????????????????")
                    }
                }
                mobileClickMenu.dismiss()
            }
        }
        mobileClickMenu.showAtLocation(main_content, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 0)
        ZoneUtil.lightOff(this)
    }

    override fun isUsuallyPerson(flag: Boolean) {
        if (flag) {
            image_person_collection.setImageResource(R.mipmap.icon_collection_enable_50dp)
            tv_person_collection_text.text = getString(R.string.person_collect_cancel)
            hasCollection = true
        } else {
            image_person_collection.setImageResource(R.mipmap.icon_collection_disable_50dp)
            tv_person_collection_text.text = getString(R.string.person_collect)
            hasCollection = false
        }
    }

    override fun loadPersonInfo(personInfo: PersonJson) {
        hideLoadingDialog()
        loadedPersonId = personInfo.id
        loadedPersonDN = personInfo.distinguishedName
        tv_person_mobile.text = personInfo.mobile
        tv_person_email.text = personInfo.mail
        if (GenderTypeEnums.FEMALE.key == personInfo.genderType) {
            linear_person_gender_women_button.visible()
            linear_person_gender_men_button.gone()
        } else {
            linear_person_gender_women_button.gone()
            linear_person_gender_men_button.visible()
        }
        genderName = GenderTypeEnums.getNameByKey(personInfo.genderType)
        if (!TextUtils.isEmpty(personInfo.signature)) {
            tv_person_sign.text = "????????????: ".plus(personInfo.signature)
        }
        tv_person_name.text = personInfo.name
        tv_person_name_2.text = personInfo.name
        if (personInfo.woIdentityList.isNotEmpty()) {
            var department = ""
            personInfo.woIdentityList.mapIndexed { index, woIdentityListItem ->
                if (index != personInfo.woIdentityList.size - 1) {
                    department += woIdentityListItem.unitName + ","
                } else {
                    department += woIdentityListItem.unitName
                }
            }
            tv_person_department.text = department
        }
        tv_person_employee.text = personInfo.employee
        tv_person_distinguishedName.text = personInfo.distinguishedName
        val url = APIAddressHelper.instance().getPersonAvatarUrlWithId(personInfo.id)
        O2ImageLoaderManager.instance().showImage(image_person_avatar, url, O2ImageLoaderOptions(placeHolder = R.mipmap.icon_avatar_men))

//        //IM ???????????????????????????
//        JMessageClient.getUserInfo(loadedPersonId, object : GetUserInfoCallback(){
//            override fun gotResult(responseCode: Int, responseMessage: String?, info: UserInfo?) {
//                XLog.info("responseCode:$responseCode, responseMessage:$responseMessage ")
//                canTalkTo = responseCode == 0
//            }
//        })
    }

    override fun loadPersonInfoFail() {
        hideLoadingDialog()
    }

    override fun createConvSuccess(conv: IMConversationInfo) {
        O2ChatActivity.startChat(this, conv.id!!)
    }

    override fun createConvFail(message: String) {
        XLog.error(message)
        XToast.toastShort(this, "??????????????????????????????????????????")
    }
}
