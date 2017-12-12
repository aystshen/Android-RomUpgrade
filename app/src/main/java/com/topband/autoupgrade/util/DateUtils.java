package com.topband.autoupgrade.util;

import java.util.Calendar;
import java.util.Date;

/**
 * Created by shenhaibo on 2017/3/26.
 */

public class DateUtils {

    public static Date getPreDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        date = calendar.getTime();
        return date;
    }

    public static Date getNextDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        date = calendar.getTime();
        return date;
    }
}
