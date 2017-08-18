package test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Test {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Set set = new HashSet();
	    set.add(new Date());   //向列表中添加数据

	    set.add("apple");    //向列表中添加数据

	    //set.add("TV");     //向列表中添加数据

	    set.add("Banana"); 
	    
	    List list=new ArrayList();

	    list.add("apple");    //向列表中添加数据

	    list.add("TV");    //向列表中添加数据

	    boolean contains = set.containsAll(list);

	    if (contains) {

	   System.out.println("Set集合包含List集合的内容");

	    } else {

	   System.out.println("Set集合不包含List集合的内容");

	    }
	}

}
