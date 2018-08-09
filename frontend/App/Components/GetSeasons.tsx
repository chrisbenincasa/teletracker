
import React, { Component } from 'react';
import { View } from 'react-native';
import { Thing } from '../Model/external/themoviedb';
import getMetadata from './Helpers/getMetadata';
import { Card, CardContent, Title, ListAccordion, ListItem  } from 'react-native-paper'

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
                <Card style={styles.seasonsContainer}>
                    <CardContent>
                        <Title style={styles.seasonsHeader}>Season Guide:</Title>
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
                    </CardContent>
                </Card>
            : null
        )
    }
};