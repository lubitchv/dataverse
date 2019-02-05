cd /usr/local/solr
bin/solr stop
rm -r solr-7.3.0
cd /usr/local
sudo rm -r glassfish4
sudo unzip glassfish-4.1.zip
sudo chown -R $USER /usr/local/glassfish4
sudo -i -u postgres
psql  -c 'DROP DATABASE "dvndb"' template1
cd /usr/local/solr
tar xvfz solr-7.3.0.tgz
cd solr-7.3.0/server/solr
cp -r configsets/_default collection1
cp ~/work/dataverse-iqss/dataverse/conf/solr/7.3.0/schema.xml .
mv schema.xml collection1/conf
cp ~/work/dataverse-iqss/dataverse/conf/solr/7.3.0/solrconfig.xml .
mv solrconfig.xml collection1/conf/solrconfig.xml
cd /usr/local/solr/solr-7.3.0
bin/solr start
bin/solr create_core -c collection1 -d server/solr/collection1/conf
cd ~/work/dataverse-iqss/dataverse/scripts/installer/
./install
cd /usr/local/glassfish4/glassfish/bin
./asadmin list-jvm-options | egrep 'fqdn'
./asadmin delete-jvm-options "-Ddataverse.fqdn=utl-192-123"
./asadmin create-jvm-options "-Ddataverse.fqdn=utl-192-123.library.utoronto.ca"
./asadmin list-jvm-options | egrep 'doi'
./asadmin delete-jvm-options "-Ddoi.username="
./asadmin create-jvm-options "-Ddoi.username=apitest"
./asadmin delete-jvm-options "-Ddoi.password="
./asadmin create-jvm-options "-Ddoi.password=apitest"
./asadmin delete-jvm-options "-Ddoi.baseurlstring="
./asadmin create-jvm-options "-Ddoi.baseurlstring=http\://ezid.cdlib.org"
curl -X PUT -d EZID http://localhost:8080/api/admin/settings/:DoiProvider
sudo -i -u postgres
 \i upgrade.sql
