package com.myproject;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.IamInstanceProfileSpecification;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.ShutdownBehavior;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

import static javax.xml.bind.DatatypeConverter.printBase64Binary;

public class Ec2Utils {
    public static void run(AmazonEC2 machines, String pathToScript, int count, String profileArn) throws IOException {
        machines.runInstances(
                new RunInstancesRequest()
                        .withImageId("ami-c7c0d6b3") // This used to be the official, Ireland running, 32 bit Amazon Machine Image. Or pick, for instance, [Ubuntu](http://cloud-images.ubuntu.com/locator/ec2/)
                        .withInstanceType(InstanceType.T1Micro) // Smallest possible, cheapest. Be warned: Cc28xlarge can set you back 3.75$ per call per machine per hour... [Pricing](http://aws.amazon.com/fr/ec2/#pricing)
                        .withMaxCount(count)
                        .withMinCount(count)
                        .withInstanceInitiatedShutdownBehavior(ShutdownBehavior.Terminate)
                        .withIamInstanceProfile(new IamInstanceProfileSpecification().withArn(profileArn))
                        .withUserData(printBase64Binary(FileUtils.readFileToString(new File(pathToScript), "UTF-8").getBytes("UTF-8")))
        );
    }

}
