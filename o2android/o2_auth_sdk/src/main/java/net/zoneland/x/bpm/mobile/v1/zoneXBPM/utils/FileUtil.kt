package net.zoneland.x.bpm.mobile.v1.zoneXBPM.utils

import android.content.ContentUris
import android.content.Context
import android.content.res.AssetManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.*
import java.nio.channels.FileChannel


/**
 * Created by FancyLou on 2015/12/18.
 */
object FileUtil {

    private val MIMETypes = arrayOf(arrayOf(".conf", "text/plain"), arrayOf(".cpp", "text/plain"), arrayOf(".c", "text/plain"), arrayOf(".java", "text/plain"), arrayOf(".log", "text/plain"), arrayOf(".json", "text/plain"), arrayOf(".prop", "text/plain"), arrayOf(".rc", "text/plain"), arrayOf(".sh", "text/plain"), arrayOf(".txt", "text/plain"), arrayOf(".xml", "text/plain"), arrayOf(".h", "text/plain"),

            arrayOf(".gif", "image/gif"), arrayOf(".jpeg", "image/jpeg"), arrayOf(".jpg", "image/jpeg"), arrayOf(".png", "image/png"), arrayOf(".bmp", "image/bmp"),

            arrayOf(".3gp", "video/3gpp"), arrayOf(".asf", "video/x-ms-asf"), arrayOf(".avi", "video/x-msvideo"), arrayOf(".m4u", "video/vnd.mpegurl"), arrayOf(".m4v", "video/x-m4v"), arrayOf(".mov", "video/quicktime"), arrayOf(".mp4", "video/mp4"), arrayOf(".mpe", "video/mpeg"), arrayOf(".mpeg", "video/mpeg"), arrayOf(".mpg", "video/mpeg"), arrayOf(".mpg4", "video/mp4"),

            arrayOf(".m3u", "audio/x-mpegurl"), arrayOf(".m4a", "audio/mp4a-latm"), arrayOf(".m4b", "audio/mp4a-latm"), arrayOf(".m4p", "audio/mp4a-latm"), arrayOf(".mp2", "audio/x-mpeg"), arrayOf(".mp3", "audio/x-mpeg"), arrayOf(".mpga", "audio/mpeg"), arrayOf(".ogg", "audio/ogg"), arrayOf(".rmvb", "audio/x-pn-realaudio"),

            arrayOf(".bin", "application/octet-stream"), arrayOf(".apk", "application/vnd.android.package-archive"), arrayOf(".class", "application/octet-stream"),

            arrayOf(".doc", "application/msword"), arrayOf(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"), arrayOf(".xls", "application/vnd.ms-excel"), arrayOf(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), arrayOf(".exe", "application/octet-stream"),

            arrayOf(".htm", "text/html"), arrayOf(".html", "text/html"),

            arrayOf(".js", "application/x-javascript"), arrayOf(".mpc", "application/vnd.mpohun.certificate"), arrayOf(".msg", "application/vnd.ms-outlook"), arrayOf(".pdf", "application/pdf"), arrayOf(".pps", "application/vnd.ms-powerpoint"), arrayOf(".ppt", "application/vnd.ms-powerpoint"), arrayOf(".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"), arrayOf(".rtf", "application/rtf"), arrayOf(".tar", "application/x-tar"), arrayOf(".tgz", "application/x-compressed"), arrayOf(".gtar", "application/x-gtar"), arrayOf(".gz", "application/x-gzip"), arrayOf(".jar", "application/java-archive"), arrayOf(".wav", "audio/x-wav"), arrayOf(".wma", "audio/x-ms-wma"), arrayOf(".wmv", "audio/x-ms-wmv"), arrayOf(".wps", "application/vnd.ms-works"), arrayOf(".z", "application/x-compress"), arrayOf(".zip", "application/x-zip-compressed"), arrayOf("", "*/*"))


    /**
     * ????????????????????????
     */
    fun appCacheDir(context: Context): File? = context.cacheDir

    /**
     * ????????????????????????
     */
    fun appExternalCacheDir(context: Context): File? = context.externalCacheDir

    /**
     * ??????sd???????????????
     */
    fun appExternalImageDir(context: Context): File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)

    /**
     * sd????????????
     */
    fun sdcardDir() = Environment.getExternalStorageDirectory()


    /**
     * ??????assets ????????????????????????????????????
     */
    fun copyFromAssets(assets: AssetManager, source: String, dest: String, isCover: Boolean = false) {
        val destFile = File(dest)
        if (isCover || (!isCover && !destFile.exists())) {
            var `is`: InputStream? = null
            var fos: FileOutputStream? = null
            try {
                `is` = assets.open(source)
                fos = FileOutputStream(dest)
                val buffer = ByteArray(1024)
                var size = 0
                if (`is`!=null) {
                    do {
                        size = `is`.read(buffer, 0, 1024)
                        if (size>0) {
                            fos?.write(buffer, 0, size)
                        }
                    }while (size>0)
                }
            } finally {
                if (fos != null) {
                    try {
                        fos.close()
                    } finally {
                        if (`is` != null) {
                            `is`.close()
                        }
                    }
                }
            }
        }
    }

    /**
     * ????????????
     */
    fun copyFileWithFileChannel(fileSource: File, fileDest:File) {
        var fi: FileInputStream? = null
        var fo: FileOutputStream? = null
        var `in`: FileChannel? = null
        var out: FileChannel? = null
        try {
            fi = FileInputStream(fileSource)
            fo = FileOutputStream(fileDest)
            `in` = fi.channel//???????????????????????????
            out = fo.channel//???????????????????????????
            `in`!!.transferTo(0, `in`.size(), out)//??????????????????????????????in???????????????????????????out??????
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                fi!!.close()
                `in`!!.close()
                fo!!.close()
                out!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }


    /**
     * android 7 ????????????uri???????????????
     */
    fun getUriFromFile(context: Context, file: File): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val applicationId = context.applicationContext.packageName
            FileProvider.getUriForFile(context, "$applicationId.fileProvider", file)
        } else {
            Uri.fromFile(file)
        }
    }


    /**
     * ?????????????????????????????????????????????????????????
     * @param context
     * @param uri
     * @return
     */
    fun getAbsoluteFilePath(context: Context, uri: Uri): String? {
        // DocumentProvider
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val type = split[0]

                if ("primary".equals(type, ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                }

            } else if (isDownloadsDocument(uri)) {

                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id)!!)

                return getDataColumn(context, contentUri, null, null)
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val type = split[0]

                var contentUri: Uri? = null
                if ("image" == type) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                } else if ("video" == type) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else if ("audio" == type) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }

                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])

                return getDataColumn(context, contentUri, selection, selectionArgs)
            }// MediaProvider
            // DownloadsProvider
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            return getDataColumn(context, uri, null, null)
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }// File
        // MediaStore (and general)

        return null
    }


    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context
     * The context.
     * @param uri
     * The Uri to query.
     * @param selection
     * (Optional) Filter used in the query.
     * @param selectionArgs
     * (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    fun getDataColumn(context: Context, uri: Uri?, selection: String?,
                      selectionArgs: Array<String>?): String? {

        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(column)

        try {
            cursor = context.contentResolver.query(uri!!, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val column_index = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(column_index)
            }
        } finally {
            if (cursor != null)
                cursor.close()
        }
        return null
    }

    fun getMIMEType(file: File): String {
        var type = "application/octet-stream"
        val fName = file.name
        //??????????????????????????????"."???fName???????????????
        val dotIndex = fName.lastIndexOf(".")
        if (dotIndex < 0) {
            return type
        }
        /* ????????????????????????*/
        val end = fName.substring(dotIndex, fName.length).toLowerCase()
        if (end === "") return type
        //???MIME?????????????????????????????????????????????MIME?????????
        for (i in MIMETypes.indices) {
            if (end == MIMETypes[i][0]) {
                type = MIMETypes[i][1]
                break
            }
        }
        return type
    }


    /**
     * @param uri
     * The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    /**
     * @param uri
     * The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    /**
     * @param uri
     * The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

}
