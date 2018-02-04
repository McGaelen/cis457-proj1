#include <sys/socket.h>
#include <sys/time.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define PACKET_SIZE 1032 // 8 bytes of header

int main(int argc, char **argv) {

    if (argc < 2) {
        printf("Must supply a port number.\n");
        exit(1);
    }

    int sockfd = socket(AF_INET, SOCK_DGRAM, 0);
    struct timeval timeout;
    timeout.tv_sec = 5;
    timeout.tv_usec = 0;
    setsockopt(sockfd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));

    FILE *f;
    char window[1024 * 5];
    int filesize;
    int numPackets;
    char payload[PACKET_SIZE];

    char portnum[20];
    strcpy(portnum, argv[1]);
    int pnum = atoi(portnum);

    struct sockaddr_in serveraddr, clientaddr;
    serveraddr.sin_family = AF_INET;
    serveraddr.sin_port = htons(pnum);
    serveraddr.sin_addr.s_addr = INADDR_ANY;

    printf("Connected to port %d\n", pnum);

    bind(sockfd, (struct sockaddr *) &serveraddr, sizeof(serveraddr));

    while (1) {
        printf("Waiting to receive...\n");
        int len = sizeof(clientaddr);
        char filename[500];
        int n = recvfrom(sockfd, filename, 5000, 0, (struct sockaddr *) &clientaddr, &len);
        if (n == -1) {
            ; // do nothing
        } else {
            strtok(filename, "\n"); // chop off newline
            printf("\nSearching for file: %s\n", filename);
            f = fopen(filename, "r");
            fseek(f, 0, SEEK_END);
            numPackets = (ftell(f) / 1024);
            printf("File is %d packets long\n", numPackets);
            rewind(f);

            fread(window, 1024 * 5, 1, f);
            window[1024 * 5] = '\0';

            unsigned char numPacketsBytes[4];
            numPacketsBytes[0] = (n >> 24) & 0xFF;
            numPacketsBytes[1] = (n >> 16) & 0xFF;
            numPacketsBytes[2] = (n >> 8) & 0xFF;
            numPacketsBytes[3] = n & 0xFF;

            for (int i = 0; i < numPackets; i++) {
                // fread(payload, 1024, 1, f);
                *payload = '\0';
                printf("Sending Packet %d\n", i+1);
                // unsigned int packetNum = i+1;
                unsigned char packetNumBytes[4];
                packetNumBytes[0] = (i >> 24) & 0xFF;
                packetNumBytes[1] = (i >> 16) & 0xFF;
                packetNumBytes[2] = (i >> 8) & 0xFF;
                packetNumBytes[3] = i & 0xFF;
                // char packetNumStr[4];
                // char numPacketsStr[4];
                // packetNumStr[4] = '\0';
                // numPacketsStr[4] = '\0';
                //
                // snprintf(packetNumStr, 4, "%u", packetNum);
                // snprintf(numPacketsStr, 4, "%u", numPackets);
                // strcat(payload, packetNumBytes);

                // fprintf(stderr, "packetNumBytes: %d\n", *packetNumBytes);
                // fprintf(stderr, "numPacketsBytes: %d\n", *numPacketsBytes);
                memcpy(payload, &i, 4);

                fwrite(payload, 4, 1, stderr);
                fprintf(stdout, "payload: %d\n", payload);

                // strcat(payload, numPacketsBytes);
                memcpy(payload[4], &numPackets, 4);
                fprintf(stdout, "payload: %s\n", payload);
                fprintf(stdout, "payload size: %d\n", sizeof(payload));
                fprintf(stderr, "payload packetNum: %u\n", atoi(payload));
                fprintf(stderr, "payload numPackets: %u\n", atoi(payload[4]));

                payload[PACKET_SIZE] = '\0';
                fprintf(stderr, "%s\n", payload);
                strncat(payload, window, 1024);
                fprintf(stderr, "payload added: %s\n", payload);

                sendto(sockfd, payload, strlen(payload)+1, 0, (struct sockaddr *) &clientaddr, sizeof(clientaddr));
            }
        }
        close((struct sockaddr *) &clientaddr);
    }
}
