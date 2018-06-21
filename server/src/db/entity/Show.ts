// import 'reflect-metadata';

// import { Entity, Index, ManyToMany } from 'typeorm';

// import { MediaItem } from './MediaItem';
// import { ShowList } from './ShowList';
// import { Network } from './Network';

// @Entity('shows')
// @Index(['externalSource', 'externalId'], { unique: true })
// export class Show extends MediaItem {
//     type: string = "show"

//     @ManyToMany(type => ShowList, list => list.shows)
//     showLists: ShowList[]

//     @ManyToMany(type => Network, list => list.shows)
//     networks: Network[]
// }