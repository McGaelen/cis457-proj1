#include <sys/socket.h>
#include <sys/time.h>
#include <netinet/in.h>
#include <stdio.h>
#include <string.h>

#define PACKET_SIZE 1032

int main(int argc, char **argv) {

    if (argc < 3) {
        printf("Must suply a port number and IP address.\n");
        exit(1);
    }

    int sockfd = socket(AF_INET, SOCK_DGRAM, 0);
    struct timeval timeout;
    timeout.tv_sec = 5;
    timeout.tv_usec = 0;
    setsockopt(sockfd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));

    char portnum[20];
    char ipaddress[20];
    strcpy(portnum, argv[1]);
    strcpy(ipaddress, argv[2]);
    int pnum = atoi(portnum);

    struct sockaddr_in serveraddr, clientaddr;
    serveraddr.sin_family = AF_INET;
    serveraddr.sin_port = htons(pnum);
    serveraddr.sin_addr.s_addr = inet_addr(ipaddress);

    printf("Connected to port %d\n", pnum);
    printf("Connected to IP %s\n", ipaddress);

    printf("Enter a filename: ");
    char filename[500];
    char payload[1024];
    fgets(filename, 500, stdin);
    int len = sizeof(clientaddr);
    sendto(sockfd, filename, strlen(filename)+1, 0, (struct sockaddr *) &serveraddr, sizeof(serveraddr));

    FILE *outFile;
    outFile = fopen("outFile.txt", "w");
    while (recvfrom(sockfd, payload, PACKET_SIZE, 0, (struct sockaddr *) &serveraddr, &len) > 0) {
        unsigned long packetNum = strtol(payload, payload[4], 0);
        unsigned long numPackets = strtol(payload[4], payload[8], 0);
        printf("\n\nWriting Packet Number %lu / %lu\n", packetNum, numPackets);
        fwrite(payload[8], 1024, 1, outFile);
    }

    printf("File transfer finished.\n");
    close(sockfd);

    return 0;

}
