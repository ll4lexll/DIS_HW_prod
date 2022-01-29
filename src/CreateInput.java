import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;
import java.util.Vector;

public class CreateInput
{
    public static int[][][] weightsMatrix;

    private static String getIP(int routerID)
    {
        return "127.0.0.1";
    }

    private static int getRandomWeight(Random random, int maximumWeight)
    {
        return random.nextInt(maximumWeight) + 1;
    }

    private static void writeNeighborData(FileWriter routerFile, int neighbor, String neighborIP, int neighborUDPport,
                                         int neighborTCPport, int weightToNeighbor) throws IOException
    {
        routerFile.write(neighbor + System.lineSeparator());
        routerFile.write(neighborIP + System.lineSeparator());
        routerFile.write(neighborUDPport + System.lineSeparator());
        routerFile.write(neighborTCPport + System.lineSeparator());
        routerFile.write(weightToNeighbor + System.lineSeparator());
    }

    public static void createRouterInputAndWeights(String routerInputFilePrefix, double selectNeighborProbability,
                                                   double changeWeightProbability, int firstUDPport, int firstTCPport,
                                                   int maximumWeight, int networkSize, int maximumUpdateRounds,
                                                   Random random) throws IOException
    /*
    Creat a strongly connected graph of routers and their input files. It also creates all the required weights updates
     */
    {
        FileWriter[] routersInputFiles = new FileWriter[networkSize + 1];
        weightsMatrix = new int[networkSize + 1][networkSize + 1][maximumUpdateRounds + 1];
        Vector<Integer> neighbors;

        for (int router = 1; router <= networkSize; router++)
        {
            routersInputFiles[router] = new FileWriter(routerInputFilePrefix + router + ".txt");
        }

        int weightedDiameter = 0;
        int neighbor;
        int routerUDPport;
        int routerTCPport;
        int neighborUDPport;
        int neighborTCPport;
        int weightToNeighbor;

        for (int router=1; router <= networkSize; router++)
        {
            neighbors = new Vector<>();

            routerUDPport = firstUDPport + router - 1;
            routerTCPport = firstTCPport + router - 1;

            neighbor = (router % networkSize) + 1;
            neighbors.add(neighbor);

            neighborUDPport = firstUDPport + neighbor - 1;
            neighborTCPport = firstTCPport + neighbor - 1;

            weightToNeighbor = getRandomWeight(random, maximumWeight);
            weightedDiameter += weightToNeighbor;

            routersInputFiles[router].write(routerUDPport + System.lineSeparator());
            routersInputFiles[router].write(routerTCPport + System.lineSeparator());
            routersInputFiles[router].write(networkSize + System.lineSeparator());

            writeNeighborData(routersInputFiles[router], neighbor, getIP(neighbor), neighborUDPport, neighborTCPport,
                    weightToNeighbor);

            double probability;
            int newNeighborUDPport;
            int newNeighborTCPport;
            int weightToNewNeighbor;

            for(int newNeighbor = 1; newNeighbor <= networkSize; newNeighbor++)
            {
                if (newNeighbor == router || newNeighbor == neighbor)
                {
                    continue;
                }
                probability = random.nextFloat();
                if (probability < selectNeighborProbability)
                {
                    neighbors.add(newNeighbor);
                    newNeighborUDPport = firstUDPport + newNeighbor - 1;
                    newNeighborTCPport = firstTCPport + newNeighbor - 1;

                    weightToNewNeighbor = getRandomWeight(random, maximumWeight);
                    weightedDiameter += weightToNewNeighbor;

                    writeNeighborData(routersInputFiles[router], newNeighbor, getIP(newNeighbor), newNeighborUDPport,
                            newNeighborTCPport, weightToNewNeighbor);
                }
            }
            // all routers neighbors have been set. Now we define the weights to each neighbor throughout the execution
            int weight;
            for (int round = 1; round <= maximumUpdateRounds; round++)
            {
                for (Integer currentNeighbor : neighbors)
                {
                    probability = random.nextFloat();
                    if (probability < changeWeightProbability)
                    {
                        weight = getRandomWeight(random, maximumWeight);
                        weightsMatrix[router][currentNeighbor][round] = weight;
                    }
                    else
                    {
                        weightsMatrix[router][currentNeighbor][round] = -1;
                    }
                }
            }
        }
        for (int router = 1; router <= networkSize; router++)
        {
            routersInputFiles[router].write("*" + System.lineSeparator());
            routersInputFiles[router].write(String.valueOf(weightedDiameter));
            routersInputFiles[router].close();
        }
    }
}
