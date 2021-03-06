package core;

import java.awt.Point;
import java.util.HashMap;
import java.util.List;

/**
 * Created on 17-4-16.
 * 出租车的相关信息
 */
class Taxi implements GlobalConstant,Cloneable{
    private int currentStatus;
    private int currentCredit;
    private int currentPosition;
    private int taxiCode;
    private int currentRow;
    private int currentCol;
    private HashMap<String,PassengerRequest> grabRequest;
    Taxi(int code){
        /*@REQUIRES:0<=code<=99
        @MODIFIES:\all member vars
        @EFFECTS:构造
        */
        taxiCode = code;
        currentStatus = WAIT_SERVICE;
        currentCredit = CREDIT_INIT;
        currentPosition = (int)(Math.random()*NODE_NUM);
        currentRow = Main.getRowByCode(currentPosition);
        currentCol = Main.getColByCode(currentPosition);
        Main.gui.SetTaxiStatus(taxiCode,new Point(currentRow,currentCol),currentStatus);
        grabRequest = new HashMap<>();
    }
    @Override
    synchronized public Taxi clone(){
        /*@REQUIRES:None
        @MODIFIES:None
        @EFFECTS:normal_behavior:拷贝this对象一份,返回
                 拷贝失败==>exceptional_behavior:(CloneNotSupportedException)打印异常处理栈
        @THREAD_REQUIRES:\locked(this)
        @THREAD_EFFECTS:\locked(),方法同步
        */
        Taxi taxi = null;
        try {
            taxi = (Taxi)super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        assert taxi!=null;/* if copy failed,throw error. */
        return taxi;
    }

    synchronized void setCurrentStatus(int currentStatus) {
        /*@REQUIRES:Contains(currentStatus).{STOP_SERVICE,WAIT_SERVICE,IN_SERVICE,GRAB_SERVICE,STOP_GRAB,STOP_ACHIEVE};
        @MODIFIES:this.currentStatus,Main.gui
        @EFFECTS:修改出租车的状态,更新对应gui上出租车的状态
        @THREAD_REQUIRES:\locked(this)
        @THREAD_EFFECTS:\locked(),方法同步
        */
        this.currentStatus = currentStatus;
        Main.gui.SetTaxiStatus(taxiCode,new Point(currentRow,currentCol),(currentStatus>GRAB_SERVICE)?STOP_SERVICE:currentStatus);
    }
    //只对应完成服务增加的信用
    synchronized void addCurrentCredit() {
        /*@REQUIRES:None
        @MODIFIES:this.currentCredit
        @EFFECTS:增加出租车的信用
        @THREAD_REQUIRES:\locked(this)
        @THREAD_EFFECTS:\locked(),方法同步
        */
        this.currentCredit += ADD_PER_SERVICE;
    }
    //设置出租车当前的位置
    synchronized void setCurrentPosition(int currentPosition) {
        /*@REQUIRES:0<=currentPosition<=6399
        @MODIFIES:this.currentPosition,Main.gui
        @EFFECTS:修改出租车的位置,更新gui
        @THREAD_REQUIRES:\locked(this)
        @THREAD_EFFECTS:\locked(),方法同步
        */
        this.currentPosition = currentPosition;
        this.currentRow = Main.getRowByCode(currentPosition);
        this.currentCol = Main.getColByCode(currentPosition);
        try {
            assert currentPosition<=6400;
            Main.gui.SetTaxiStatus(taxiCode, new Point(currentRow, currentCol), (currentStatus > GRAB_SERVICE) ? STOP_SERVICE : currentStatus);
        }catch(Exception ignored){
            //System.out.println("excuse me.?");
            /* I found the gui dont't resolve thread safety. */
            /* Some time in a low-low rate you may found the guigv.flowmap throw NullPointerException */
            /* Because the cause is from gui,and occui in a extremely low rate,So ignore it to prove the taxi's normal running  */
            //just to ignore gui self-exception.
        }
    }
    //this function not use.
    /*synchronized void setCurrentPosition(int row,int col){
        this.currentRow = row;
        this.currentCol = col;
        this.currentPosition = Main.getCodeByRowCol(row,col);
    }*/

    synchronized void searchAblePick(){
        /*@REQUIRES:None
        @MODIFIES:Main.mapSignal,Main.safeFilePassenger,System.out，grabRequest
        @EFFECTS:查询出租车在当前位置是否存在该出租车可以抢的单,可以抢单,则将该出租车编号写入对应被抢请求的grabTaxis中
                并将抢单的信息输出到文件以及终端
        @THREAD_REQUIRES:\locked(this)
        @THREAD_EFFECTS:\locked(),方法同步
        */
        //只有当出租车当前的状态是等待服务状态才可以,其他的一律否决
        if(currentStatus==WAIT_SERVICE) {
            List<PassengerRequest> list = Main.mapSignal.getMapSignalAt(currentPosition);
            for (PassengerRequest aList : list) {
                String hashKey = aList.toHashString();
                if(grabRequest.get(hashKey)==null) {
                    grabRequest.put(hashKey,aList);
                    aList.addGrabTaxi(taxiCode);
                    currentCredit += ADD_PER_GRAB;
                    //一旦抢单成功就需要输出信息到SafeFile
                    String info = Main.getCurrentTime() + "s 被" + taxiCode +
                            "号出租车抢单.位置:(" + currentRow + "," + currentCol + ")\t信用值:" + currentCredit;
                    Main.safeFilePassenger.writeToFile(hashKey, info);
                    Main.outPutInfoToTerminal(hashKey + "\t" + info);
                }
            }
        }
    }

    synchronized void clearHashMap(){
        /*@REQUIRES:None
        @MODIFIES:this.currentCredit
        @EFFECTS:清除出租车所有的抢单集合
        @THREAD_REQUIRES:\locked(grabRequest)
        @THREAD_EFFECTS:\locked(),方法同步
        */
        this.grabRequest.clear();
    }
    @Override
    synchronized public String toString(){
        /*@REQUIRES:None
        @MODIFIES:None
        @EFFECTS:返回出租车String形式
        @THREAD_REQUIRES:\locked(this)
        @THREAD_EFFECTS:\locked(),方法同步
        */
        String info = "出租车当前位置:(";
        int row = Main.getRowByCode(currentPosition);
        int col = Main.getColByCode(currentPosition);
        info+=row+","+col+")\t出租车当前状态:";
        switch (currentStatus){
            case STOP_SERVICE:
                info+="停止服务.";
                break;
            case STOP_GRAB:
                info+="到达接客地,停车中.";
                break;
            case STOP_ACHIEVE:
                info+="完成服务,停车中.";
                break;
            case IN_SERVICE:
                info+="已接客,正在服务.";
                break;
            case WAIT_SERVICE:
                info+="等待服务状态.";
                break;
            case GRAB_SERVICE:
                info+="抢单成功并分配服务,正在赶往接客.";
                break;
            default:break;
        }
        return info+"\t出租车当前信用值:"+currentCredit;
    }
    //因为克隆,所以get方法不加锁,线程安全
    int getCurrentStatus() {
        /*@REQUIRES:None
        @MODIFIES:None
        @EFFECTS:返回currentStatus的值
        */
        return currentStatus;
    }

    int getCurrentCredit() {
        /*@REQUIRES:None
        @MODIFIES:None
        @EFFECTS:返回currentCredit的值
        */
        return currentCredit;
    }

    int getCurrentPosition() {
        /*@REQUIRES:None
        @MODIFIES:None
        @EFFECTS:返回currentPosition的值
        */
        return currentPosition;
    }

    int getCurrentRow(){
        /*@REQUIRES:None
        @MODIFIES:None
        @EFFECTS:返回currentRow的值
        */
        return currentRow;
    }

    int getCurrentCol(){
        /*@REQUIRES:None
        @MODIFIES:None
        @EFFECTS:返回currentCol的值
        */
        return currentCol;
    }

    int getTaxiCode() {
        /*@REQUIRES:None
        @MODIFIES:None
        @EFFECTS:返回taxiCode的值
        */
        return taxiCode;
    }
}
