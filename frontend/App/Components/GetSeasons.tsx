
import React, { Component } from 'react';
import { Text, View } from 'react-native';
import { Avatar } from 'react-native-elements';
import { ListAccordion, ListItem, Divider } from 'react-native-paper';
import { Thing } from '../Model/external/themoviedb';
import getMetadata from './Helpers/getMetadata';
import { parseInitials } from './Helpers/textHelper';

import styles from './Styles/GetSeasons';

interface Props {
    item?: Thing
}

export default class GetSeasons extends Component {
    constructor(props: Props) {
        super(props);
    }

    render () {
        return (
            getMetadata.getSeasons(this.props.item) ?
                <View style={styles.seasonsContainer}>
                    <Divider style={styles.divider} />
                    <Text style={styles.seasonsHeader}>Season Guide:</Text>
                    <View
                        style={styles.avatarContainer}>
                        {
                            getMetadata.getSeasons(this.props.item) ? getMetadata.getSeasons(this.props.item).map((i) => (
                                
                                <ListAccordion
                                    title="Accordion"
                                    icon={ 
                                        i.poster_path ? { 
                                            uri: 'https://image.tmdb.org/t/p/w92' + i.poster_path 
                                        } : 'broken-image' 
                                    }
                                    key={i.id}
                                >
                                    <ListItem title="Episode 1" />
                                    <ListItem title="Episode 2" />
                                </ListAccordion>
                                
                                // <View key={i.id}>
                                //     <Avatar
                                //         key={i.id}
                                //         large
                                //         rounded
                                //         // {{ uri: 'https://avatars0.githubusercontent.com/u/17571969?v=3&s=400' }}
                                //         source={
                                //             i.poster_path
                                //                 ? {
                                //                     uri: "https://image.tmdb.org/t/p/w92" + i.poster_path
                                //                 }
                                //                 : null}
                                //         activeOpacity={0.7}
                                //         title={i.poster_path ? null : parseInitials(i.name)}
                                //         titleStyle={parseInitials(i.name).length > 2 ? { fontSize: 26 } : null}
                                //     />
                                //     <Text style={styles.seasonsName}>{i.name}</Text>
                                // </View>
                            ))
                                : null
                        }
                    </View>
                </View>
            : null
        )
    }
};