figure; 
hold on;
num = 6999;

clamped1 = load('clamped4.csv');

A = sortrows(clamped1,4);
clusterID = 1;
c = A;
c(1,4) = clusterID;
for i=2:num,
    if (c(i,5)~=c(i-1,5))
        clusterID = clusterID + 1;
    end
    c(i,4) = clusterID;
end

numClusters = clusterID;
total = zeros(numClusters,1);

for n=1:numClusters,
    index = 1; 
    while (index~=6999 && c(index,4)<=n),
     index = index + 1;
    end
    clamped = sum(c(1:index,3));
    if (index~=6999),
        unclamped = sum(c((index+1):num,2));
    else
        unclamped = 0;
    end
    total(n) = (clamped +  unclamped)/num;
    
end
plot(1:numClusters,total,'b');

clamped2 = load('clamped5.csv');

A = sortrows(clamped2,4);
clusterID = 1;
c = A;
c(1,4) = clusterID;
for i=2:num,
    if (c(i,5)~=c(i-1,5))
        clusterID = clusterID + 1;
    end
    c(i,4) = clusterID;
end

numClusters = clusterID;
total = zeros(numClusters,1);

for n=1:numClusters,
    index = 1; 
    while (index~=6999 && c(index,4)<=n),
     index = index + 1;
    end
    clamped = sum(c(1:index,3));
    if (index~=6999),
        unclamped = sum(c((index+1):num,2));
    else
        unclamped = 0;
    end
    total(n) = (clamped +  unclamped)/num;
end
plot(1:numClusters,total,'r');


clamped3 = load('clamped6.csv');

A = sortrows(clamped3,4);
clusterID = 1;
c = A;
c(1,4) = clusterID;
for i=2:num,
    if (c(i,5)~=c(i-1,5))
        clusterID = clusterID + 1;
    end
    c(i,4) = clusterID;
end

numClusters = clusterID;
total = zeros(numClusters,1);

for n=1:numClusters,
    index = 1; 
    while (index~=6999 && c(index,4)<=n),
     index = index + 1;
    end
    clamped = sum(c(1:index,3));
    if (index~=6999),
        unclamped = sum(c((index+1):num,2));
    else
        unclamped = 0;
    end
    total(n) = (clamped +  unclamped)/num;
end
plot(1:numClusters,total,'g');


% clamped4 = load('clamped12.csv');
% 
% A = sortrows(clamped4,4);
% clusterID = 1;
% c = A;
% c(1,4) = clusterID;
% for i=2:num,
%     if (c(i,5)~=c(i-1,5))
%         clusterID = clusterID + 1;
%     end
%     c(i,4) = clusterID;
% end
% 
% numClusters = clusterID;
% total = zeros(numClusters,1);
% 
% for n=1:numClusters,
%     index = 1; 
%     while (index~=6999 && c(index,4)<=n),
%      index = index + 1;
%     end
%     clamped = sum(c(1:index,3));
%     if (index~=6999),
%         unclamped = sum(c((index+1):num,2));
%     else
%         unclamped = 0;
%     end
%     total(n) = (clamped +  unclamped)/num;
% end
% plot(1:numClusters,total,'c');

xlabel('# Citations/Questions Asked');
ylabel('Total Accuracy');
title('Clustering Algorithm: Same Label Neighorhood');
legend('Highest Entropy', 'Cluster Size', 'Total Entropy');