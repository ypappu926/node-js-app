require('dotenv').config();

const awsDocumentDBURI = 'mongodb://amit:amit1234@node-db.cluster-cpm60sek0z1k.ap-south-1.docdb.amazonaws.com:27017/?tls=true&tlsCAFile=global-bundle.pem&replicaSet=rs0&readPreference=secondaryPreferred&retryWrites=false'

module.exports = {
  mongoURI: awsDocumentDBURI,
  redisPort: process.env.REDIS_PORT,
  redisHost: process.env.REDIS_HOST
};
