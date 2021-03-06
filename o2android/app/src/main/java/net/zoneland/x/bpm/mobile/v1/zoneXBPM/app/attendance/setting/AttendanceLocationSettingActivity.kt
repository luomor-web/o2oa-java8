package net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.attendance.setting


import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import com.baidu.location.BDLocation
import com.baidu.location.BDLocationListener
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.map.*
import com.baidu.mapapi.model.LatLng
import kotlinx.android.synthetic.main.activity_attendance_location_setting.*
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.R
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.base.BaseMVPActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.bo.api.attendance.MobileCheckInWorkplaceInfoJson
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.XLog
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.XToast
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.extension.text2String
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.widgets.dialog.O2DialogSupport


class AttendanceLocationSettingActivity : BaseMVPActivity<AttendanceLocationSettingContract.View, AttendanceLocationSettingContract.Presenter>(),
        AttendanceLocationSettingContract.View, BDLocationListener {
    override var mPresenter: AttendanceLocationSettingContract.Presenter = AttendanceLocationSettingPresenter()
    override fun layoutResId(): Int = R.layout.activity_attendance_location_setting

    val WORK_PLACE_ID = "WORK_PLACE_ID"

    val mLocationClient: LocationClient by lazy { LocationClient(this) }
    lateinit var mBaiduMap: BaiduMap
    var marker: Marker? = null
    var latitude = ""
    var longitude = ""


    override fun afterSetContentView(savedInstanceState: Bundle?) {
        setupToolBar(getString(R.string.title_activity_attendance_location_setting), true)
        mLocationClient.registerLocationListener(this)

        initBaiduLocation()
        mBaiduMap = map_attendance_location_setting_baidu.map
        mBaiduMap.mapType = BaiduMap.MAP_TYPE_NORMAL
        mBaiduMap.setOnMapClickListener(object : BaiduMap.OnMapClickListener {
            override fun onMapClick(latLng: LatLng?) {
                XLog.debug("onMapClick latitude:${latLng?.latitude}, longitude:${latLng?.longitude}")
                markerPoint(latLng)
            }

            override fun onMapPoiClick(poi: MapPoi?): Boolean {
                val latLng = poi?.position
                XLog.debug("onMapPoiClick latitude:${latLng?.latitude}, longitude:${latLng?.longitude}")
                markerPoint(latLng)
                return false
            }
        })
        mBaiduMap.setOnMarkerClickListener { marker ->
            val bundle = marker.extraInfo
            if (bundle != null) {
                val id = bundle.getString(WORK_PLACE_ID)
                deleteWorkplace(id)
            }
            false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_attendance_location_setting, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_work_place_save -> {
                saveWorkplace()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }


    override fun onResume() {
        super.onResume()
        //???activity??????onResume?????????mMapView. onResume ()?????????????????????????????????
        map_attendance_location_setting_baidu.onResume()
        refreshMap()
    }

    override fun onPause() {
        super.onPause()
        map_attendance_location_setting_baidu.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        map_attendance_location_setting_baidu.onDestroy()
    }

    override fun onReceiveLocation(location: BDLocation?) {
        if (location == null) {
            return
        }
        XLog.debug("onReceiveLocation locType:${location.locType}")
        val latitude = location.latitude
        val longitude = location.longitude
        XLog.info("????????????,address:${location.addrStr}, latitude:$latitude, longitude:$longitude")
        //??????Maker?????????
        val point = LatLng(latitude, longitude)
        //???????????????????????????
        val builder = MapStatus.Builder().target(point).zoom(18.0f)
        mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()))
        //????????????
        mLocationClient.stop()
    }

    override fun onConnectHotSpotMessage(p0: String?, p1: Int) {
        XLog.info("connectHotSpot $p0, $p1")
    }

    override fun deleteWorkplace(flag: Boolean) {
        hideLoadingDialog()
        if (flag) {
            XToast.toastShort(this, "???????????????????????????")
            refreshMap()
        }else {
            XToast.toastShort(this, "???????????????????????????")
        }
    }

    override fun saveWorkplace(flag: Boolean) {
        hideLoadingDialog()
        if (flag) {
            XToast.toastShort(this, "???????????????????????????")
            edit_attendance_location_setting_error_range.setText("")
            edit_attendance_location_setting_name.setText("")
            refreshMap()
        }else {
            XToast.toastShort(this, "???????????????????????????")
        }
    }

    override fun workplaceList(list: List<MobileCheckInWorkplaceInfoJson>) {
        val bitmap = BitmapDescriptorFactory
                .fromResource(R.mipmap.icon_map_location_green)
        list.map {
            val point = LatLng(it.latitude.toDouble(), it.longitude.toDouble())
            val bundle = Bundle()
            bundle.putString(WORK_PLACE_ID, it.id)
            val options = MarkerOptions()
                    .position(point)  //??????marker?????????
                    .title(it.placeName)
                    .extraInfo(bundle)
                    .icon(bitmap)  //??????marker??????
                    .zIndex(9)
            mBaiduMap.addOverlay(options)
        }
    }

    private fun refreshMap() {
        mBaiduMap.clear()
        mLocationClient.start()
        mPresenter.loadAllWorkplace()
    }

    private fun saveWorkplace() {
        val name = edit_attendance_location_setting_name.text2String()
        if (TextUtils.isEmpty(name)) {
            XToast.toastShort(this, "?????????????????????????????????")
            return
        }
        if (TextUtils.isEmpty(latitude) || TextUtils.isEmpty(longitude) ) {
            XToast.toastShort(this, "??????????????????????????????????????????")
            return
        }
        var errorRange = edit_attendance_location_setting_error_range.text2String()
        if (TextUtils.isEmpty(errorRange)) {
            errorRange = "100"
        }
        showLoadingDialog()
        mPresenter.saveWorkplace(name, errorRange, latitude, longitude)
    }

    private fun deleteWorkplace(id: String?) {
        if (TextUtils.isEmpty(id)) {
            XLog.error("id is null!!!!")
            return
        }
        O2DialogSupport.openConfirmDialog(this, "???????????????????????????????????????",{
            showLoadingDialog()
            mPresenter.deleteWorkplace(id!!)
        })
    }

    private fun markerPoint(latLng: LatLng?) {
        if (latLng==null) {
            XLog.error("????????????")
            return
        }
        latitude =latLng.latitude.toString()
        longitude = latLng.longitude.toString()
        if (marker == null) {
            //??????Marker??????
            val bitmap = BitmapDescriptorFactory
                    .fromResource(R.mipmap.icon_map_location)
            val options = MarkerOptions()
                    .position(latLng)  //??????marker?????????
                    .icon(bitmap)  //??????marker??????
                    .zIndex(9)
            //???marker??????????????????
            marker = mBaiduMap.addOverlay(options) as Marker
        }else {
            marker?.position = latLng
        }

    }

    private fun initBaiduLocation() {
        val option = LocationClientOption()
        option.locationMode = LocationClientOption.LocationMode.Hight_Accuracy//?????????????????????????????????????????????????????????????????????????????????
        option.setCoorType("bd09ll")//???????????????gcj02???????????????????????????????????????
        option.setScanSpan(0)//???????????????0???????????????????????????????????????????????????????????????????????????1000ms???????????????
        option.setIsNeedAddress(true)//?????????????????????????????????????????????????????????
        option.isOpenGps = true//???????????????false,??????????????????gps
        option.isLocationNotify = true//???????????????false??????????????????GPS???????????????1S/1???????????????GPS??????
        option.setIsNeedLocationDescribe(true)//???????????????false??????????????????????????????????????????????????????BDLocation.getLocationDescribe?????????????????????????????????????????????????????????
        option.setIsNeedLocationPoiList(true)//???????????????false?????????????????????POI??????????????????BDLocation.getPoiList?????????
        option.setIgnoreKillProcess(false)//???????????????true?????????SDK???????????????SERVICE?????????????????????????????????????????????stop?????????????????????????????????????????????
        option.SetIgnoreCacheException(false)//???????????????false?????????????????????CRASH?????????????????????
        option.setEnableSimulateGps(false)//???????????????false???????????????????????????GPS???????????????????????????
        mLocationClient.locOption = option
    }

}
