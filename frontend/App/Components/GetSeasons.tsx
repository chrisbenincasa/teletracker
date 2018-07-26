
import React, { Component } from 'react';
import { Text, View, ScrollView } from 'react-native';
import { Avatar, Divider } from 'react-native-elements';
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
                    <ScrollView
                        horizontal={true}
                        showsHorizontalScrollIndicator={false}
                        style={styles.avatarContainer}>
                        {
                            getMetadata.getSeasons(this.props.item) ? getMetadata.getSeasons(this.props.item).map((i) => (
                                <View key={i.id}>
                                    <Avatar
                                        key={i.id}
                                        large
                                        rounded
                                        source={
                                            i.poster_path
                                                ? {
                                                    uri: "https://image.tmdb.org/t/p/w92" + i.poster_path
                                                }
                                                : null}
                                        activeOpacity={0.7}
                                        title={i.poster_path ? null : parseInitials(i.name)}
                                        titleStyle={parseInitials(i.name).length > 2 ? { fontSize: 26 } : null}
                                    />
                                    <Text style={styles.seasonsName}>{i.name}</Text>
                                </View>
                            ))
                                : null
                        }
                    </ScrollView>
                </View>
            : null
        )
    }
};