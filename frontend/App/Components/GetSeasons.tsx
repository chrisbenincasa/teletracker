
import React, { Component } from 'react';
import { Text, View } from 'react-native';
import { ListAccordion, ListItem, Divider } from 'react-native-paper';
import { Thing } from '../Model/external/themoviedb';
import getMetadata from './Helpers/getMetadata';

import styles from './Styles/GetSeasons';

interface Props {
    item?: Thing
}

export default class GetSeasons extends Component {
    constructor(props: Props) {
        super(props);
        console.log(getMetadata.getSeasons(this.props.item));
    }

    render () {
        return (
            getMetadata.getSeasons(this.props.item) ?
                <View style={styles.seasonsContainer}>
                    <Divider style={styles.divider} />
                    <Text style={styles.seasonsHeader}>Season Guide:</Text>
                    <View>
                        {
                            getMetadata.getSeasons(this.props.item) ? getMetadata.getSeasons(this.props.item).map((i) => (
                                <ListAccordion
                                    key={i.id}    
                                    title={`${i.name} (${i.episode_count})`}
                                    icon={
                                        i.poster_path ? { 
                                            uri: `https://image.tmdb.org/t/p/w92${i.poster_path}`
                                        } : 'broken-image' 
                                    }
                                >
                                    <ListItem title='Episode 1' />
                                </ListAccordion>
                            ))
                                : null
                        }
                    </View>
                </View>
            : null
        )
    }
};