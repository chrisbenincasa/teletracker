import { MovieDbClient } from 'themoviedb-client-typed';
import { MigrationInterface, QueryRunner, Entity } from 'typeorm';

import { GlobalConfig } from '../../Config';
import { Certification, CertificationType } from '../entity/Certification';

export class SeedCertifications1529958157000 implements MigrationInterface {
    movieDbClient = new MovieDbClient(GlobalConfig.themoviedb.apiKey);
    
    async up(queryRunner: QueryRunner): Promise<any> {
        const certifications = await this.movieDbClient.certifications.getMovieCertifications();
        let certModels: Certification[] = [];

        for (let [region, certs] of certifications) {
            certs.forEach(cert => {
                let c = new Certification();
                
                c.iso_3166_1 = region;
                if (!cert.certification || !cert.meaning || !cert.order) {
                    console.log('region ' + region + ' missing some vital info');
                } else {
                    c.type = CertificationType.Movie
                    c.certification = cert.certification;
                    c.description = cert.meaning;
                    c.order = cert.order;
                    certModels.push(c);
                }
            });
        }

        const tvCertifications = await this.movieDbClient.certifications.getTvCertifications();
        for (let [region, certs] of tvCertifications) {
            certs.forEach(cert => {
                let c = new Certification();

                c.iso_3166_1 = region;
                if (!cert.certification || !cert.meaning || !cert.order) {
                    console.log('region ' + region + ' missing some vital info');
                } else {
                    c.type = CertificationType.Tv
                    c.certification = cert.certification;
                    c.description = cert.meaning;
                    c.order = cert.order;
                    certModels.push(c);
                }
            });
        }

        await queryRunner.manager.insert(Certification, certModels);
    }
    
    async down(queryRunner: QueryRunner): Promise<any> {
        await queryRunner.query(`TRUNCATE TABLE "certifications"`);
    }
}