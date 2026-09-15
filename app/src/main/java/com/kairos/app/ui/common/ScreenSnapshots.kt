package com.kairos.app.ui.common

import com.kairos.app.data.remote.dto.BooksDto
import com.kairos.app.data.remote.dto.ChoresDto
import com.kairos.app.data.remote.dto.GroceriesDto
import com.kairos.app.data.remote.dto.ReadingDto
import com.kairos.app.data.remote.dto.SchoolDto
import com.kairos.app.data.remote.dto.TasksListDto

/**
 * Last-loaded data per section, held in app-singleton memory so returning to a
 * section renders instantly from it instead of a spinner. Each screen seeds from
 * here when its ViewModel is (re)created and writes back on a successful load;
 * the network refresh still runs in the background. Cleared on sign-out. Money is
 * deliberately excluded (per-user select could briefly show the wrong person).
 * Calendar keeps its own view-keyed snapshot.
 */
object ScreenSnapshots {
    var chores: ChoresDto? = null
    var tasks: TasksListDto? = null
    var groceries: GroceriesDto? = null
    var reading: BooksDto? = null
    var school: SchoolDto? = null
    var bible: ReadingDto? = null

    fun clearAll() {
        chores = null
        tasks = null
        groceries = null
        reading = null
        school = null
        bible = null
    }
}
