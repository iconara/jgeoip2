# encoding: utf-8

module JGeoIP2
  describe Database do
    let :database do
      described_class.open(db_path)
    end

    let :db_path do
      File.expand_path('../../resources/maxmind-db/test-data/GeoIP2-City-Test.mmdb', __FILE__)
    end

    describe '.open' do
      it 'returns a database' do
        expect(described_class.open(db_path)).to be_a(described_class)
      end

      context 'when given a path that does not exist' do
        it 'raises an error' do
          expect { described_class.open('foobaz') }.to raise_error(IOError, /foobaz \(no such file or directory\)/i)
          expect { described_class.open(nil) }.to raise_error(ArgumentError, /path is required/i)
        end
      end

      context 'when given a malformed database' do
        it 'raises an error' do
          expect { described_class.open(__FILE__) }.to raise_error(ArgumentError, /malformed database: metadata section not found/i)
        end
      end
    end

    describe '#metadata' do
      context 'returns an object that' do
        it 'knows the database format version' do
          expect(database.metadata.format_version).to eq('2.0')
        end

        it 'knows the build time of the database' do
          expect(database.metadata.build_time).to eq(Time.utc(2016, 2, 19, 16, 51, 56))
        end

        it 'knows the database type' do
          expect(database.metadata.database_type).to eq('GeoIP2-City')
        end

        it 'knows the database languages' do
          expect(database.metadata.languages).to contain_exactly('en', 'zh')
        end

        it 'knows the database description' do
          expect(database.metadata.description).to eq('GeoIP2 City Test Database (fake GeoIP2 data, for example purposes only)')
        end

        it 'knows the database description in multiple languages' do
          expect(database.metadata.description('en')).to eq('GeoIP2 City Test Database (fake GeoIP2 data, for example purposes only)')
          expect(database.metadata.description('zh')).to eq("小型数据库")
        end

        it 'knows the IP version of the database' do
          expect(database.metadata.ip_version).to eq(6)
        end
      end
    end

    describe '#get' do
      it 'returns a hash' do
        expect(database.get('2.125.160.216')).to be_a(Hash)
      end

      context 'when given a malformed IP' do
        it 'raises an error'
      end
    end
  end
end
