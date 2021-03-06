package net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.clouddrive.v2.f

import android.graphics.Typeface
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.text.TextUtils
import android.util.TypedValue
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.android.synthetic.main.fragment_file_folder_list.*
import net.muliba.changeskin.FancySkinManager
import net.muliba.fancyfilepickerlibrary.FilePicker
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.R
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.base.BaseMVPFragment
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.clouddrive.v2.CloudDiskActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.clouddrive.v2.picker.CloudDiskFolderPickerActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.clouddrive.v2.type.FileTypeEnum
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.clouddrive.v2.viewer.BigImageViewActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.app.o2.organization.ContactPickerActivity
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.bo.FileBreadcrumbBean
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.bo.api.yunpan.FileJson
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.bo.api.yunpan.FolderJson
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.model.vo.CloudDiskItem
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.MiscUtilK
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.XLog
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.XToast
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.extension.gone
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils.extension.visible
import net.zoneland.x.bpm.mobile.v1.zoneXBPM.widgets.dialog.O2DialogSupport
import java.io.File
import java.util.HashMap


class FileFolderListFragment : BaseMVPFragment<FileFolderListContract.View, FileFolderListContract.Presenter>(), FileFolderListContract.View {



    override var mPresenter: FileFolderListContract.Presenter = FileFolderListPresenter()

    override fun layoutResId(): Int  = R.layout.fragment_file_folder_list

    companion object {
        val ARG_FOLDER_ID_KEY = "ARG_FOLDER_ID_KEY"
        val LPWW = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    private val font: Typeface by lazy { Typeface.createFromAsset(activity?.assets, "fonts/fontawesome-webfont.ttf") }
    private val breadcrumbBeans = ArrayList<FileBreadcrumbBean>()//?????????????????????
    private val adapter: CloudDiskItemAdapter by lazy { CloudDiskItemAdapter() }
    private var fileLevel = 0//?????????????????????????????????

    override fun initData() {
        super.initData()
        //?????????????????????????????????????????????ActionItem
        setHasOptionsMenu(true)
    }

    override fun initUI() {
        if (arguments != null) {
            if (arguments!!.getSerializable(ARG_FOLDER_ID_KEY) != null) {
                val bean = arguments!!.getSerializable(ARG_FOLDER_ID_KEY) as FileBreadcrumbBean
                breadcrumbBeans.clear()
                breadcrumbBeans.add(bean)
            }
        }
        if (breadcrumbBeans.isEmpty()) {
            val top = FileBreadcrumbBean()
            top.displayName = getString(R.string.yunpan_all_file)
            top.folderId = ""
            top.level = 0
            breadcrumbBeans.add(top)
        }

        swipe_refresh_file_folder_layout.setColorSchemeResources(R.color.z_color_refresh_scuba_blue,
                R.color.z_color_refresh_red, R.color.z_color_refresh_purple, R.color.z_color_refresh_orange)
        swipe_refresh_file_folder_layout.setOnRefreshListener { refreshView() }

        initRecyclerView()

        initToolbarListener()

        MiscUtilK.swipeRefreshLayoutRun(swipe_refresh_file_folder_layout, activity)
        refreshView()
    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_cloud_disk, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.cloud_disk_menu_upload_file -> {
                menuUploadFile()
                return true
            }
            R.id.cloud_disk_menu_create_folder -> {
                menuCreateFolder()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun onClickBackBtn(): Boolean {
        if (breadcrumbBeans.size > 1) {
            breadcrumbBeans.removeAt(breadcrumbBeans.size - 1)//??????????????????
            refreshView()
            return true
        }
        return false
    }

    override fun itemList(list: List<CloudDiskItem>) {
        swipe_refresh_file_folder_layout.isRefreshing = false
        adapter.items.clear()
        adapter.items.addAll(list)
        adapter.clearSelectIds()
        adapter.notifyDataSetChanged()
        refreshToolBar()
    }


    override fun error(error: String) {
        XToast.toastShort(activity, error)
        swipe_refresh_file_folder_layout.isRefreshing = false
        hideLoadingDialog()
    }

    override fun createFolderSuccess() {
        hideLoadingDialog()
        XToast.toastShort(activity, "????????????????????????")
        refreshView()
    }



    override fun uploadSuccess() {
        hideLoadingDialog()
        XToast.toastShort(activity, "???????????????")
        refreshView()
    }

    override fun updateSuccess() {
        hideLoadingDialog()
        XToast.toastShort(activity, "???????????????")
        refreshView()
    }

    override fun deleteSuccess() {
        hideLoadingDialog()
        XToast.toastShort(activity, "???????????????")
        refreshView()
    }

    override fun shareSuccess() {
        hideLoadingDialog()
        refreshView()
        XToast.toastShort(activity, "???????????????")
    }

    override fun moveSuccess() {
        hideLoadingDialog()
        refreshView()
        XToast.toastShort(activity, "???????????????")
    }

    private fun refreshView() {
        swipe_refresh_file_folder_layout.isRefreshing = true
        val current = breadcrumbBeans.last()
        loadFileList(current.folderId, current.level)
        if (breadcrumbBeans.size == 1) {
            if (activity is CloudDiskActivity) {
                (activity as CloudDiskActivity).showCategoryArea()
            }
        }else {
            if (activity is CloudDiskActivity) {
                (activity as CloudDiskActivity).hideCategoryArea()
            }
        }
        loadBreadcrumb()
    }

    /**
     * ?????????????????????
     */
    private fun initToolbarListener() {
        btn_file_folder_rename.setOnClickListener {
            XLog.debug("click rename button ")
            if (adapter.mSelectIds.size == 1) {
                renameFile()
            } else {
                XToast.toastShort(activity, "???????????????????????????????????????")
            }
        }
        btn_file_folder_delete.setOnClickListener {
            XLog.debug("click delete button ")
            if (adapter.mSelectIds.isNotEmpty()) {
                delete()
            } else {
                XToast.toastShort(activity, "????????????????????????????????????????????????")
            }
        }
        btn_file_folder_share.setOnClickListener {
            XLog.debug("click share button ")
            if (adapter.mSelectIds.isNotEmpty()) {
                share()
            } else {
                XToast.toastShort(activity, "????????????????????????????????????????????????")
            }
        }
        btn_file_folder_move.setOnClickListener {
            XLog.debug("click share button ")
            if (adapter.mSelectIds.isNotEmpty()) {
                move()
            } else {
                XToast.toastShort(activity, "????????????????????????????????????????????????")
            }
        }
    }

    private fun move() {
        //???????????????????????????
        CloudDiskFolderPickerActivity.pickFolder(activity!!) { parentId ->
            val files = ArrayList<CloudDiskItem.FileItem>()
            val folders = ArrayList<CloudDiskItem.FolderItem>()
            adapter.mSelectIds.forEach { id ->
                val item = adapter.items.firstOrNull { id == it.id }
                if (item != null) {
                    when(item) {
                        is CloudDiskItem.FileItem -> files.add(item)
                        is CloudDiskItem.FolderItem -> folders.add(item)
                    }
                }
            }
            showLoadingDialog()
            mPresenter.move(files, folders, parentId)
        }
    }

    private fun share() {
        val bundle = ContactPickerActivity.startPickerBundle(
                arrayListOf(ContactPickerActivity.personPicker, ContactPickerActivity.departmentPicker),
                multiple = true)
        if (activity is CloudDiskActivity) {
            (activity as CloudDiskActivity).contactPicker(bundle) { result ->
                if (result != null) {
                    val person = result.users.map { it.distinguishedName }
                    val orgs = result.departments.map { it.distinguishedName }
                    showLoadingDialog()
                    mPresenter.share(adapter.mSelectIds.toList(), person, orgs)
                }
            }
        }
    }

    private fun delete() {
        O2DialogSupport.openConfirmDialog(activity, "????????????????????????????????????", { dialog ->
            val fileids = ArrayList<String>()
            val folderids = ArrayList<String>()
            adapter.mSelectIds.forEach { id ->
                val item = adapter.items.firstOrNull { id == it.id }
                if (item != null) {
                    when(item) {
                        is CloudDiskItem.FileItem -> fileids.add(item.id)
                        is CloudDiskItem.FolderItem -> folderids.add(item.id)
                    }
                }
            }
            showLoadingDialog()
            mPresenter.deleteBatch(fileids, folderids)
        })
    }

    private fun renameFile() {
        val renameId = adapter.mSelectIds.first()
        val item = adapter.items.firstOrNull { it.id == renameId }
        if (item != null) {
            val dialog = O2DialogSupport.openCustomViewDialog(activity!!, getString(R.string.yunpan_rename), R.layout.dialog_name_modify) {
                dialog ->
                val text = dialog.findViewById<EditText>(R.id.dialog_name_editText_id)
                val content = text.text.toString()
                if (TextUtils.isEmpty(content)) {
                    XToast.toastShort(activity, "?????????????????????")
                } else {
                    showLoadingDialog()
                    if (item is CloudDiskItem.FolderItem) {
                        val folderJson = FolderJson(item.id, item.createTime, item.updateTime, content,
                                item.person, "", item.superior, item.attachmentCount, item.size,
                                item.folderCount, item.status, item.fileId)
                        mPresenter.updateFolder(folderJson)
                    } else if (item is CloudDiskItem.FileItem) {
                        val fileJson = FileJson(item.id, item.createTime, item.updateTime, content, item.person,
                                "", item.fileName, item.extension, item.contentType, item.storageName, item.fileId,
                                item.storage, item.type, item.length, item.folder,
                                item.lastUpdateTime, item.lastUpdatePerson)
                        mPresenter.updateFile(fileJson)
                    }
                    dialog.dismiss()

                }
            }
            val text = dialog.findViewById<EditText>(R.id.dialog_name_editText_id)
            text.setText(item.name)
        }
    }

    /**
     * ????????? ??????
     */
    private fun initRecyclerView() {
        rv_file_folder_list.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)
        rv_file_folder_list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val topRowVerticalPosition = rv_file_folder_list?.getChildAt(0)?.top ?: 0
                swipe_refresh_file_folder_layout.isEnabled = topRowVerticalPosition >= 0
            }
        })
        rv_file_folder_list.adapter = adapter
        adapter.onItemClickListener = object : CloudDiskItemAdapter.OnItemClickListener {

            override fun onFolderClick(folder: CloudDiskItem.FolderItem) {
                //???????????????
                XLog.debug("??????????????????" + folder.name)
                val newLevel = fileLevel + 1
                val newBean = FileBreadcrumbBean()
                newBean.displayName = folder.name
                newBean.folderId = folder.id
                newBean.level = newLevel
                breadcrumbBeans.add(newBean)
                refreshView()
            }

            override fun onFileClick(file: CloudDiskItem.FileItem) {
                if (file.type == FileTypeEnum.image.key) {
                    BigImageViewActivity.start(activity!!, file.id, file.extension, file.name)
                }else {
                    if (activity is CloudDiskActivity) {
                        (activity as CloudDiskActivity).openFile(file)
                    }
                }
            }
        }
        adapter.onCheckChangeListener = object : CloudDiskItemAdapter.OnCheckChangeListener {
            override fun onChange() {
                refreshToolBar()
            }
        }
    }

    /**
     * ?????????????????????
     */
    private fun loadBreadcrumb() {
        ll_file_folder_breadcrumb.removeAllViews()
        breadcrumbBeans.mapIndexed { index, fileBreadcrumbBean ->
            val breadcrumbTitle = TextView(activity)
            breadcrumbTitle.text = fileBreadcrumbBean.displayName
            breadcrumbTitle.tag = fileBreadcrumbBean
            breadcrumbTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            breadcrumbTitle.layoutParams = LPWW
            if (index == breadcrumbBeans.size - 1) {
                breadcrumbTitle.setTextColor(FancySkinManager.instance().getColor(activity!!, R.color.z_color_primary))
                ll_file_folder_breadcrumb.addView(breadcrumbTitle)
            } else {
                breadcrumbTitle.setTextColor(FancySkinManager.instance().getColor(activity!!, R.color.z_color_text_primary_dark))
                breadcrumbTitle.setOnClickListener { v -> onClickBreadcrumb(v as TextView) }
                ll_file_folder_breadcrumb.addView(breadcrumbTitle)
                val arrow = TextView(activity)
                val lp = LPWW
                lp.setMargins(8, 0, 8, 0)
                arrow.layoutParams = lp
                arrow.text = getString(R.string.fa_angle_right)
                arrow.setTextColor(FancySkinManager.instance().getColor(activity!!, R.color.z_color_text_primary_dark))
                arrow.typeface = font
                arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                ll_file_folder_breadcrumb.addView(arrow)
            }
        }
    }

    /**
     * ?????????????????????
     */
    private fun onClickBreadcrumb(textView: TextView) {
        val bean = textView.tag as FileBreadcrumbBean
        var newLevel = 0
        breadcrumbBeans.mapIndexed { index, fileBreadcrumbBean ->
            if (bean == fileBreadcrumbBean) {
                newLevel = index
            }
        }
        //??????breadcrumbBeans ??????????????????
        if (breadcrumbBeans.size > (newLevel + 1)) {
            val s = breadcrumbBeans.size
            for (i in (s-1) downTo (newLevel+1)) {
                println(i)
                breadcrumbBeans.removeAt(i)
            }
        }
        refreshView()
    }

    private fun loadFileList(id: String, newLevel: Int) {
        fileLevel = newLevel
        mPresenter.getItemList(id)
    }


    private fun menuUploadFile() {
        FilePicker()
                .withActivity(activity!!)
                .chooseType(FilePicker.CHOOSE_TYPE_SINGLE)
                .forResult { filePaths ->
                    if (filePaths.isNotEmpty()) {
                        uploadFile(filePaths[0])
                    }
                }
    }
    private fun uploadFile(filePath: String) {
        XLog.debug("filePath=$filePath")
        try {
            val upFile = File(filePath)
            val bean = breadcrumbBeans.last()//????????????
            showLoadingDialog()
            mPresenter.uploadFile(bean.folderId, upFile)
        } catch (e: Exception) {
            XLog.error("", e)
            XToast.toastShort(activity, "?????????????????????")
        }
    }

    /**
     * ???????????????
     */
    private fun menuCreateFolder() {
        O2DialogSupport.openCustomViewDialog(activity!!, getString(R.string.yunpan_menu_create_folder), R.layout.dialog_name_modify) {
            dialog ->
            val text = dialog.findViewById<EditText>(R.id.dialog_name_editText_id)
            val content = text.text.toString()
            if (TextUtils.isEmpty(content)) {
                XToast.toastShort(activity, "??????????????????????????????")
            } else {
                createFolderOnLine(content)
                dialog.dismiss()
            }
        }
    }

    private fun createFolderOnLine(folderName: String) {
        val params = HashMap<String, String>()
        params["name"] = folderName
        if (breadcrumbBeans.size > 1) {
            val bean = breadcrumbBeans.last()//????????????
            params["superior"] = bean.folderId
        } else {
            params["superior"] = ""
        }
        mPresenter.createFolder(params)
    }

    /**
     * ?????? ???????????????
     */
    private fun refreshToolBar() {
        if (adapter.mSelectIds.isEmpty()) {
            ll_file_folder_toolbar.gone()
        }else {
            ll_file_folder_toolbar.visible()
            if (adapter.mSelectIds.size > 1) {
                btn_file_folder_rename.isEnabled = false
                btn_file_folder_delete.isEnabled = true
                btn_file_folder_share.isEnabled = true
                btn_file_folder_move.isEnabled = true
            }else {
                btn_file_folder_rename.isEnabled = true
                btn_file_folder_delete.isEnabled = true
                btn_file_folder_share.isEnabled = true
                btn_file_folder_move.isEnabled = true
            }
        }
    }

}