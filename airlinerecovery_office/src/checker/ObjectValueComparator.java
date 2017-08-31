package checker;

import java.util.Comparator;

public class ObjectValueComparator implements Comparator<ObjectValue> {

	@Override
	public int compare(ObjectValue arg0, ObjectValue arg1) {
		// TODO Auto-generated method stub
		if(arg0.value > arg1.value) {
			return -1;
		}else if(arg0.value < arg1.value) {
			return 1;
		}else {
			return 0;
		}
	}

}
