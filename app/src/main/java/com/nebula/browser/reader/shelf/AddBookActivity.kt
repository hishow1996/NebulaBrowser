package com.nebula.browser.reader.shelf

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.nebula.browser.common.toast

class AddBookActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        toast("导入书籍 / 搜索书籍（演示）")
        finish()
    }
}
