import 'reflect-metadata';

import { Column, Entity, Index, ManyToMany, PrimaryGeneratedColumn } from 'typeorm';

import { ShowList } from './ShowList';
import { MediaItem } from './MediaItem';

@Entity('shows')
@Index(['externalSource', 'externalId'], { unique: true })
export class Show extends MediaItem {
    @ManyToMany(type => ShowList, list => list.shows)
    showLists: ShowList[]
}