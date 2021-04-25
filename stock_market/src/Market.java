import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;


public class Market {

    public static void main(String[] args) throws InterruptedException, IOException {
        // write your code here

        if (args.length != 3) {
            System.out.println("请输入正确参数！");
            return;
        }
        //通过新浪接口获取数据
        while (true) {
//            cls();
            getCustomerInfo(args[0],args[1]);
            Calendar c = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat(" HH:mm:ss");
            System.out.println(sdf.format(c.getTime()));
            Thread.sleep(Long.parseLong(args[2])*1000);
        }

//        System.out.println(xxx("48.51", "47.29"));

    }

    public static String getCustomerInfo(String name, String code) {
        OutputStreamWriter out = null;
        StringBuffer buffer = new StringBuffer();
        try {
            //1.连接部分
            URL url = new URL("http://hq.sinajs.cn/list=" + code);
            // http协议传输
            HttpURLConnection httpUrlConn = (HttpURLConnection) url.openConnection();

            httpUrlConn.setDoOutput(true);
            httpUrlConn.setDoInput(true);
            httpUrlConn.setUseCaches(false);
            // 设置请求方式（GET/POST）
            httpUrlConn.setRequestMethod("GET");

            //3.获取数据
            // 将返回的输入流转换成字符串
            InputStream inputStream = httpUrlConn.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "GB18030");
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            String str = null;
            while ((str = bufferedReader.readLine()) != null) {
                buffer.append(str);
            }
            bufferedReader.close();
            inputStreamReader.close();
            // 释放资源
            inputStream.close();
            httpUrlConn.disconnect();
            String raw = buffer.toString();
            String[] split = raw.split(";");
            for (String s : split) {
                code = s.substring(s.lastIndexOf("_") + 3, s.lastIndexOf("="));
                String substring = s.substring(s.indexOf("\"") + 1, s.lastIndexOf("\""));
                String[] split1 = substring.split(",");
                StringBuilder builder = new StringBuilder();
                builder.append(code.substring(3));
                if (name.equals("name")) {
                    builder.append(" " + split1[0]);
                }
                builder.append(" " + xxx(split1[2], split1[3]));
                builder.append(" " + split1[3]);
//                builder.append("  " + split1[2]);
                System.out.print(builder.toString()+"   ");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return "数据错误！";
    }

    public static String xxx(String a, String b) {
        java.text.DecimalFormat df = new java.text.DecimalFormat("#.00");
        double aDouble = Double.parseDouble(a);
        double bDouble = Double.parseDouble(b);
        if (aDouble < bDouble) {
            return "+" + df.format((bDouble - aDouble) / aDouble * 100) ;
        } else {
            return "-" + df.format((1 - bDouble / aDouble) * 100) ;
        }
    }

    /**
     * 控制台清屏
     *  * @throws IOException  
     *  * @throws InterruptedException
     *  
     */
    /*public static void cls() throws IOException, InterruptedException {
        new ProcessBuilder("bash", "clear").inheritIO().start().waitFor(); // 清屏命令
    }*/
}
