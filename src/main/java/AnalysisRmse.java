import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.partition.HashPartitioner;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AnalysisRmse {
    public static class AnalysisRmseMapper extends Mapper<LongWritable, Text, Text, Text> {
        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            FileSplit fileSplit = (FileSplit)context.getInputSplit();
            String interfaceInfo = fileSplit.getPath().getName();    //得到文件名
            //System.out.println(interfaceInfo);
            if(interfaceInfo.contains(".txt")&&(!interfaceInfo.contains(".crc")))
            {
                String[] log = value.toString().split("\t");
                //System.out.println(value.toString());
                String time = log[0];
                String url = ((FileSplit) context.getInputSplit()).getPath() .getName().replace(".txt","");
                // String url = path.getParent().toString();
                // String url = log[1];

                String num = log[1];
                context.write(new Text(time+"_"+url ),new Text(num));
            }

        }

    }
    public static class AnalysisRmsePartitioner extends HashPartitioner<Text, Text>
    {
        @Override
        public int getPartition(Text key, Text value, int numReduceTasks)
        {
            String[] keyInfo = key.toString().split("_");
            return super.getPartition(new Text(keyInfo[0]), value, numReduceTasks);
        }
    }
    public static class AnalysisRmseReducer extends Reducer<Text, Text, Text, Text>
    {
        private double rmse=0;
        private double currentDis=0;
        private double urlcount=0;
        private String currentTime=new String();
        @Override
        public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException
        {
            String[] keyInfo=key.toString().split("_");
            String time=keyInfo[0];
            int t=0;
            double dis = 0;
            for(Text val: values)
            {
                if (t==0)
                    dis=Double.parseDouble(val.toString());
                else if (t==1)
                    dis=Double.parseDouble(val.toString())-dis;
                else
                    break;
                t++;
            }
            if (currentTime.length()!=0&&currentTime.compareTo(time)!=0)
            {
                rmse=rmse+Math.sqrt(currentDis/urlcount);
                urlcount=1;
                currentDis=dis*dis;
            }
            else
            {
                urlcount++;
                currentDis+=dis*dis;
            }
            currentTime=time;
        }
        @Override
        public void cleanup(Context context) throws IOException, InterruptedException
        {
            rmse+=Math.sqrt(currentDis/urlcount);
            context.write(new Text("rmse:"),new Text(Double.toString(rmse/24)));
        }
    }
    public static void main(String[] args)
    {
        try {
            Job job = Job.getInstance();
            //Configuration conf = new Configuration();
            //Job job = new Job(conf, "invert index");
            job.setJobName("AnalysisRmse");
            job.setJarByClass(AnalysisRmse.class);
            job.setInputFormatClass(TextInputFormat.class);
            job.setMapperClass(AnalysisRmse.AnalysisRmseMapper.class);
            job.setPartitionerClass(AnalysisRmse.AnalysisRmsePartitioner.class);
            job.setReducerClass(AnalysisRmse.AnalysisRmseReducer.class);
            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(Text.class);
            FileInputFormat.addInputPath(job, new Path("src/main/resources/data/predictset"));
            FileInputFormat.addInputPath(job, new Path("src/main/resources/data/testset"));
            FileOutputFormat.setOutputPath(job, new Path("src/main/resources/data/outputset"));
            System.exit(job.waitForCompletion(true) ? 0 : 1);
        } catch (Exception e) { e.printStackTrace();
        }
    }
}
